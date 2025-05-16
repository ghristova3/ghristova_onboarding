package com.example.abaltachat.network

import android.util.Log
import com.example.abaltachat.domain.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.*
import java.net.Socket

class TcpClient(
    private val socket: Socket,
    private val onMessageReceived: (ChatMessage) -> Unit,
    private val fileTransferProgressListener: FileTransferProgressListener,
    private val path: String? = null,
) {
    private val messagesQueue = SocketQueue()
    private var coroutineJob: Job? = null
    private var senderJob: Job? = null

    private val dataInput = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val dataOutput = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

    private var receivingFile: File? = null
    private var receivingFileSize: Long = 0L
    private var totalReceived: Long = 0L

    fun start(scope: CoroutineScope) {
        coroutineJob = scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    when (dataInput.readByte()) {
                        MESSAGE_TYPE -> {
                            val length = dataInput.readInt()
                            val bytes = ByteArray(length)
                            dataInput.readFully(bytes)
                            val header = String(bytes)
                            handleHeader(header)
                        }

                        FILE_CHUNK -> {
                            if (receivingFile == null) {
                                Log.e(TAG, "Received file data without header!")
                                continue
                            }

                            val chunkSize = dataInput.readInt()
                            val chunk = ByteArray(chunkSize)
                            dataInput.readFully(chunk)
                            appendToFile(chunk)
                        }
                    }

                } catch (ex: IOException) {
                    Log.e(TAG, "Receiving error: ${ex.message}", ex)
                    fileTransferProgressListener.onTransferError("Unknown", ex)
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
                    delay(2)
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

                receivingFile = file
                receivingFileSize = fileSize
                totalReceived = 0

                fileTransferProgressListener.onReceivingFile(fileName, fileSize)

                Log.d(TAG, "Expecting file: $fileName ($fileSize bytes)")
            }
        }
    }

    private fun appendToFile(chunk: ByteArray) {
        val file = receivingFile ?: return
        try {
            FileOutputStream(file, true).use { fos ->
                fos.write(chunk)
            }

            totalReceived += chunk.size
            val progress = ((totalReceived * 100) / receivingFileSize).toInt()

            fileTransferProgressListener.onProgressUpdate(progress)

            if (totalReceived >= receivingFileSize) {
                fileTransferProgressListener.onFileReceived(file.name, receivingFileSize)
                receivingFile = null
                receivingFileSize = 0
                totalReceived = 0
            }

        } catch (ex: IOException) {
            fileTransferProgressListener.onTransferError(file.name, ex)
        }
    }

    fun sendMessage(message: String) {
        val messageData = "$TEXT_HEADER$message"
        messagesQueue.sendText(messageData)
    }

    fun sendFile(file: File) {
        val fileData = "$FILE_HEADER${file.name}:${file.length()}"
        try {
            val bytes = fileData.toByteArray()
            dataOutput.writeByte(MESSAGE_TYPE.toInt())
            dataOutput.writeInt(bytes.size)
            dataOutput.write(bytes)
            dataOutput.flush()
            Log.d(TAG, "Sent header: $fileData")
        } catch (ex: IOException) {
            Log.e(TAG, "Error sending header: ${ex.message}")
        }
        messagesQueue.sendFileInChunks(file, FILE_CHUNK_SIZE)
    }

    private fun sendText(message: String) {
        try {
            val data = message.toByteArray()
            dataOutput.writeByte(MESSAGE_TYPE.toInt())
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
            dataOutput.writeByte(FILE_CHUNK.toInt())
            dataOutput.writeInt(chunk.data.size)
            dataOutput.write(chunk.data)
            dataOutput.flush()
            Log.d(TAG, "Sent file chunk: ${chunk.fileName}, size: ${chunk.data.size}")
        } catch (ex: IOException) {
            Log.e(TAG, "Error sending file chunk: ${ex.message}")
        }
    }

    fun stop() {
        try {
            coroutineJob?.cancel()
            senderJob?.cancel()
            dataInput.close()
            dataOutput.close()
            socket.close()
        } catch (ex: IOException) {
            Log.e(TAG, "Error closing socket: ${ex.message}", ex)
        }
    }

    companion object {

        const val TAG = "TcpClient"
        const val MESSAGE_TYPE: Byte = 'M'.code.toByte()
        const val FILE_CHUNK: Byte = 'F'.code.toByte()
        const val TEXT_HEADER = "Text:"
        const val FILE_HEADER = "File:"
        // TODO Bigger file chucks? 10MB
        const val FILE_CHUNK_SIZE = 1024 * 1024
    }
}