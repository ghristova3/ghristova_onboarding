package com.example.abaltachat.network

import android.os.Environment
import android.util.Log
import com.example.abaltachat.domain.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.*
import java.net.Socket

class TcpClient(
    private val socket: Socket,
    private val onMessageReceived: (ChatMessage) -> Unit,
    private val fileTransferProgressListener: FileTransferProgressListener
) {
    private var coroutineJob: Job? = null
    // TODO Use Store Access framework because of Android 11
    private val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/"

    private val dataInput = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val dataOutput = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

    fun start(scope: CoroutineScope) {
//        coroutineJob = scope.launch(Dispatchers.IO) {
        while (true) {
            try {
                val header = dataInput.readUTF()
                Log.d(TAG, "Received header: $header")

                when {
                    header.startsWith(TEXT_HEADER) -> {
                        val message = header.removePrefix(TEXT_HEADER)
                        onMessageReceived(ChatMessage.TextMessage(message, isIncoming = true))
                    }

                    header.startsWith(FILE_HEADER) -> {
                        val parts = header.split(":")
                        if (parts.size < 3) {
                            Log.e(TAG, "Invalid file header: $header")
                            continue
                        }

                        val fileName = parts[1]
                        val fileSize = parts[2].toLongOrNull() ?: continue
                        val outputPath = "$path$fileName"
                        val file = File(outputPath)

                        fileTransferProgressListener.onReceivingFile(fileName, fileSize)
                        receiveFile(file, fileSize)
                    }

                    else -> Log.w(TAG, "Unknown message type: $header")
                }

            } catch (e: IOException) {
                Log.e(TAG, "Error reading header: ${e.message}", e)
                break
            }
        }
//        }
    }

    private fun receiveFile(file: File, fileSize: Long) {
        try {
            FileOutputStream(file).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    while (totalRead < fileSize) {
                        val remaining = fileSize - totalRead
                        val read = dataInput.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read == -1) break

                        bos.write(buffer, 0, read)
                        totalRead += read

                        val progress = ((totalRead * 100) / fileSize).toInt()
                        fileTransferProgressListener.onProgressUpdate(progress)
                    }
                    bos.flush()
                    fileTransferProgressListener.onFileReceived(file.name, file.length())
                    Log.d(TAG, "File received: ${file.name} ($totalRead / $fileSize bytes)")
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Error saving received file: ${ex.message}", ex)
            fileTransferProgressListener.onTransferError(file.name, ex)
        }
    }

    fun sendMessage(message: String) {
        try {
            dataOutput.writeUTF(TEXT_HEADER + message)
            dataOutput.flush()
            Log.d(TAG, "Sent message: $message")
        } catch (ex: IOException) {
            Log.e(TAG, "Error sending message: ${ex.message}", ex)
        }
    }

    fun sendFile(file: File) {
        try {
            val fileSize = file.length()
            val header = "$FILE_HEADER${file.name}:$fileSize"
            dataOutput.writeUTF(header)
            dataOutput.flush()

            var totalSent = 0L
            val buffer = ByteArray(8192)

            file.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    dataOutput.write(buffer, 0, read)
                    totalSent += read

                    val progress = ((totalSent * 100) / fileSize).toInt()
                    fileTransferProgressListener.onProgressUpdate(progress)
                }
            }

            dataOutput.flush()
            Log.d(TAG, "Sent file: ${file.name}, bytes: $totalSent / $fileSize")
        } catch (ex: IOException) {
            Log.e(TAG, "Error sending file: ${ex.message}", ex)
            fileTransferProgressListener.onTransferError(file.name, ex)
        }
    }

    fun stop() {
        try {
            coroutineJob?.cancel()
            dataInput.close()
            dataOutput.close()
            socket.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}", e)
        }
    }

    companion object {
        const val TAG = "TcpClient"

        const val TEXT_HEADER = "Text:"
        const val FILE_HEADER = "File:"
    }
}

