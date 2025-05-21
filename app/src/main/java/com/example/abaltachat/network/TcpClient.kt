package com.example.abaltachat.network

import android.util.Log
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.utils.readFullyOrNull
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.security.MessageDigest


class TcpClient(
    private val socket: Socket,
    private val onMessageReceived: (ChatMessage) -> Unit,
    private val connectionListener: ConnectionListener,
    private val path: String? = null,
) {
    private val messagesQueue = SocketQueue()
    private var coroutineJob: Job? = null
    private var senderJob: Job? = null

    private val dataInput = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val dataOutput = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
    private val messageDigest: MessageDigest = MessageDigest.getInstance("MD5")
    private val receivingFiles = mutableMapOf<String, ReceivingFileData>()

    fun start(scope: CoroutineScope) {
        coroutineJob = scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    when (dataInput.readByte()) {
                        CONTROL_MESSAGE_TYPE -> {
                            val length = dataInput.readInt()
                            val bytes = ByteArray(length)
                            dataInput.readFully(bytes)
                            val header = String(bytes)
                            handleHeader(header)
                        }

                        CONTROL_FILE_CHUNK -> {
                            try {
                                // TODO Better error handling
                                val receiveChunk = receiveChunk(dataInput)
                                Log.d(TAG, "receiveChunk $receiveChunk")
                                val chunk = receiveChunk ?: continue
                                appendToFile(chunk.fileName, chunk.data)
                            } catch (ex: IOException) {
                                Log.e(TAG, "Checksum failed, request resend|show error: ${ex.message}")
//                                connectionListener.onFileTransferError(fileName, ex)
//                                receivingFiles.remove(fileName)
                            }
                        }
                    }
                } catch (ex: IOException) {
                    Log.e(TAG, "Receiving error: ${ex.message}", ex)
                    connectionListener.onFileTransferError("Unknown", ex)
                    break
                } catch (ex: SocketException) {
                    Log.e(TAG, "SocketException error: ${ex.message}", ex)
                    connectionListener.onConnectionError(ex.message)
                    break
                }
            }
        }

        senderJob = scope.launch(Dispatchers.IO) {
            while (true) {
                if (messagesQueue.hasTextMessage()) {
                    messagesQueue.takeText()?.let { sendText(it.text) }
                } else if (messagesQueue.hasFileChunk()) {
                    messagesQueue.takeFileChunk()?.let { sendFileChunk(it) }
                    delay(SENDER_DELAY_MS)
                }
            }
        }
    }

    private fun handleHeader(header: String) {
        when {
            header.startsWith(TEXT_HEADER) -> {
                val message = header.removePrefix(TEXT_HEADER)
                onMessageReceived(ChatMessage.TextMessage(message, isIncoming = true))
            }

            header.startsWith(FILE_HEADER) -> {
                val parts = header.removePrefix(FILE_HEADER).split(":")
                if (parts.size < 2) return
                val fileName = parts[0]
                val fileSize = parts[1].toLongOrNull() ?: return
                val outputPath = if (path != null) "$path$fileName" else fileName
                val file = File(outputPath)

                receivingFiles[file.name] = ReceivingFileData(file, fileSize)
                connectionListener.onFileIncoming(file.name, fileSize)
                Log.d(TAG, "Incoming file: ${file.name} ($fileSize bytes) exists: ${file.exists()}")
            }
        }
    }

    private fun appendToFile(fileName: String, chunk: ByteArray) {
        val fileInfo = receivingFiles[fileName] ?: return
        try {

            FileOutputStream(fileInfo.file, false).use { fos ->
                fos.write(chunk)
            }

            fileInfo.totalReceived += chunk.size
            val progress = ((fileInfo.totalReceived * COMPLETED_FILE_PROGRESS) / fileInfo.size).toInt()
            connectionListener.onFileProgressUpdated(fileName, progress)

            if (fileInfo.totalReceived >= fileInfo.size) {
                connectionListener.onFileReceived(fileInfo.file)
                receivingFiles.remove(fileName)
            }

        } catch (ex: IOException) {
            Log.e(TAG, "Error appending file ${ex.message}")
            connectionListener.onFileTransferError(fileName, ex)
            receivingFiles.remove(fileName)
        }
    }

    fun sendMessage(message: String) {
        val messageData = "$TEXT_HEADER$message"
        messagesQueue.sendText(messageData)
    }

    fun sendFile(file: File) {
        writeFileHeaderData(file)
        try {
            messagesQueue.sendFileInChunks(file, FILE_CHUNK_SIZE)
        } catch (ex: OutOfMemoryError) {
            connectionListener.onFileTransferError(file.name + ex
                .message, IOException())
        }
    }

    private fun writeFileHeaderData(file: File) {
        val fileData = "$FILE_HEADER${file.name}:${file.length()}"
        try {
            val bytes = fileData.toByteArray()
            dataOutput.writeByte(CONTROL_MESSAGE_TYPE.toInt())
            dataOutput.writeInt(bytes.size)
            dataOutput.write(bytes)
            dataOutput.flush()
            Log.d(TAG, "Sent header: $fileData")
        } catch (ex: IOException) {
            Log.e(TAG, "Error sending header: ${ex.message}")
        }
    }

    private fun sendText(message: String) {
        try {
            val data = message.toByteArray()
            dataOutput.writeByte(CONTROL_MESSAGE_TYPE.toInt())
            dataOutput.writeInt(data.size)
            dataOutput.write(data)
            dataOutput.flush()
            Log.d(TAG, "Sent text: $message")
        } catch (ex: IOException) {
            Log.e(TAG, "Error sending text: ${ex.message}")
        }
    }

    private fun sendFileChunk(chunk: SocketMessage.FileChunk) {
        try {
            val fileNameBytes = chunk.fileName.toByteArray(Charsets.UTF_8)
            val md5 = messageDigest.digest(chunk.data)

            dataOutput.writeByte(CONTROL_FILE_CHUNK.toInt())
            dataOutput.writeInt(fileNameBytes.size)
            dataOutput.write(fileNameBytes)
            dataOutput.writeInt(chunk.data.size)
            dataOutput.write(md5)
            dataOutput.write(chunk.data)
            dataOutput.flush()
            Log.d(TAG, "Sent file chunk: ${chunk.fileName}, size: ${chunk.data.size}")
        } catch (ex: IOException) {
            Log.e(TAG, "Error sending file chunk: ${ex.message}")
        }
    }

    private fun receiveChunk(inputStream: InputStream): ReceivedChunk? {
        val intBuffer = ByteArray(INT_SIZE)
        if (inputStream.readFullyOrNull(intBuffer) != INT_SIZE) return null
        val fileNameLength = ByteBuffer.wrap(intBuffer).int

        val fileNameBytes = ByteArray(fileNameLength)
        if (inputStream.readFullyOrNull(fileNameBytes) != fileNameLength) return null
        val fileName = String(fileNameBytes, Charsets.UTF_8)

        if (inputStream.readFullyOrNull(intBuffer) != INT_SIZE) return null
        val chunkSize = ByteBuffer.wrap(intBuffer).int

        val expectedMd5 = ByteArray(MD5_SIZE)
        if (inputStream.readFullyOrNull(expectedMd5) != MD5_SIZE) return null

        val chunkData = ByteArray(chunkSize)
        if (inputStream.readFullyOrNull(chunkData) != chunkSize) return null

        val actualMd5 = messageDigest.digest(chunkData)
        if (!expectedMd5.contentEquals(actualMd5)) {
            throw IOException("MD5 checksum mismatch for chunk of $fileName")
        }

        return ReceivedChunk(fileName, chunkData)
    }

    fun stop() {
        try {
            coroutineJob?.cancel()
            senderJob?.cancel()
            dataInput.close()
            dataOutput.close()
            socket.close()
            receivingFiles.clear()
        } catch (ex: IOException) {
            Log.e(TAG, "Error closing socket: ${ex.message}", ex)
        }
    }

    companion object {

        const val TAG = "TcpClient"
        const val CONTROL_MESSAGE_TYPE: Byte = 'M'.code.toByte()
        const val CONTROL_FILE_CHUNK: Byte = 'F'.code.toByte()
        const val TEXT_HEADER = "Text:"
        const val FILE_HEADER = "File:"

        // TODO Bigger file chucks? 10MB
        const val FILE_CHUNK_SIZE = 1024 * 1024
        const val MD5_SIZE = 16
        const val INT_SIZE = 4
        const val SENDER_DELAY_MS = 10L
        const val COMPLETED_FILE_PROGRESS = 100
    }
}