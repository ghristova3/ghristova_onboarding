package com.example.abaltachat.network

import com.example.abaltachat.utils.Log
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.utils.readFullyOrNull
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

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
                                val (fileId, receiveChunk) = receiveChunk(dataInput)
                                if (receiveChunk == null) {
                                    connectionListener.onFileTransferError(
                                        fileId,
                                        IOException("MD5 checksum mismatch for fileId $fileId")
                                    )
                                    receivingFiles.remove(fileId)
                                    continue
                                } else {
                                    appendToFile(receiveChunk.fileId, receiveChunk.data)
                                }
                            } catch (ex: IOException) {
                                Log.e(
                                    TAG,
                                    "Checksum failed, request resend|show error: ${ex.message}"
                                )
                                connectionListener.onFileTransferError(
                                    "Checksum failed, request resend|show error",
                                    ex
                                )
                            }
                        }
                    }
                } catch (ex: IOException) {
                    Log.e(TAG, "Receiving error: ${ex.message}", ex)
                    connectionListener.onFileTransferError("Receiving error:", ex)
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
                    delay(SENDER_DELAY_MS)
                    messagesQueue.takeFileChunk()?.let { sendFileChunk(it) }
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
                // File:<fileId>:<fileName>:<fileSize>
                val parts = header.removePrefix(FILE_HEADER).split(":")
                if (parts.size < 3) return
                val fileId = parts[0]
                val fileName = parts[1]
                val fileSize = parts[2].toLongOrNull() ?: return

                val dir = File(path ?: ".")
                val uniqueFile = getFileWithUuid(dir, fileName, fileId)

                receivingFiles[fileId] = ReceivingFileData(uniqueFile, fileSize)
                connectionListener.onFileIncoming(uniqueFile.name, fileSize)
                Log.d(TAG, "Incoming file: ${uniqueFile.name} ($fileSize bytes)")
            }
        }
    }

    private fun getFileWithUuid(dir: File, originalName: String, fileId: String): File {
        val baseName = originalName.substringBeforeLast('.', originalName)
        val extension = originalName.substringAfterLast('.', "")

        val newName = if (extension.isNotEmpty()) {
            "$baseName-$fileId.$extension"
        } else {
            "$baseName-$fileId"
        }

        return File(dir, newName)
    }

    private fun appendToFile(fileId: String, chunk: ByteArray) {
        val fileInfo = receivingFiles[fileId] ?: return
        try {

            FileOutputStream(fileInfo.file, true).use { fos ->
                fos.write(chunk)
            }

            fileInfo.totalReceived += chunk.size
            val progress =
                ((fileInfo.totalReceived * COMPLETED_FILE_PROGRESS) / fileInfo.size).toInt()
            connectionListener.onFileProgressUpdated(fileInfo.file.name, progress)

            if (fileInfo.totalReceived >= fileInfo.size) {
                connectionListener.onFileReceived(fileInfo.file)
                receivingFiles.remove(fileId)
            }

        } catch (ex: IOException) {
            Log.e(TAG, "Error appending file: ${ex.message}")
            connectionListener.onFileTransferError(fileInfo.file.name, ex)
            receivingFiles.remove(fileId)
        }
    }

    fun sendMessage(message: String) {
        val messageData = "$TEXT_HEADER$message"
        messagesQueue.sendText(messageData)
    }

    fun sendFile(file: File) {
        val fileId = UUID.randomUUID().toString()
        writeFileHeaderData(fileId, file)
        try {
            sendFileChunks(fileId, file)
        } catch (ex: OutOfMemoryError) {
            connectionListener.onFileTransferError(
                file.name + ex
                    .message, IOException()
            )
        }
    }

    // For test purposes. After that fun should be private
    fun sendFileChunks(fileId: String, file: File) {
        messagesQueue.sendFileInChunks(fileId, file, FILE_CHUNK_SIZE)
    }

    fun writeFileHeaderData(fileId: String, file: File) {
        val fileData = "$FILE_HEADER$fileId:${file.name}:${file.length()}"
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
            val fileId = chunk.fileID
            val fileIdBytes = fileId.toByteArray(Charsets.UTF_8)
            val md5 = messageDigest.digest(chunk.data)

            dataOutput.writeByte(CONTROL_FILE_CHUNK.toInt())

            // fileId length + fileId
            dataOutput.writeInt(fileIdBytes.size)
            dataOutput.write(fileIdBytes)

            dataOutput.writeInt(chunk.data.size)
            dataOutput.write(md5)
            dataOutput.write(chunk.data)
            dataOutput.flush()
        } catch (ex: IOException) {
            Log.e(TAG, "Error sending file chunk: ${ex.message}")
        }
    }


    private fun receiveChunk(inputStream: InputStream): Pair<String, ReceivedChunk?> {
        val intBuffer = ByteArray(INT_SIZE)
        inputStream.readFullyOrNull(intBuffer)

        val fileIdBytes = ByteArray(ByteBuffer.wrap(intBuffer).int)
        inputStream.readFullyOrNull(fileIdBytes)
        val fileId = String(fileIdBytes, Charsets.UTF_8)

        if (inputStream.readFullyOrNull(intBuffer) != INT_SIZE) return fileId to null
        val chunkSize = ByteBuffer.wrap(intBuffer).int

        val expectedMd5 = ByteArray(MD5_SIZE)
        if (inputStream.readFullyOrNull(expectedMd5) != MD5_SIZE) return fileId to null

        val chunkData = ByteArray(chunkSize)
        if (inputStream.readFullyOrNull(chunkData) != chunkSize) return fileId to null

        val actualMd5 = messageDigest.digest(chunkData)
        if (!expectedMd5.contentEquals(actualMd5)) fileId to null

        return fileId to ReceivedChunk(fileId, chunkData)
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

        // Smaller chunks 64, 256 KB
        const val FILE_CHUNK_SIZE = 1024 * 1024
        const val MD5_SIZE = 16
        const val INT_SIZE = 4
        const val SENDER_DELAY_MS = 20L
        const val COMPLETED_FILE_PROGRESS = 100
    }
}