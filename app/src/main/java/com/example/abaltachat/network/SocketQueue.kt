package com.example.abaltachat.network

import java.io.File
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

class SocketQueue {

    private val textMessages: Queue<SocketMessage.Text> = ConcurrentLinkedQueue()
    private val fileChunks: Queue<SocketMessage.FileChunk> = ConcurrentLinkedQueue()

    fun hasTextMessage(): Boolean = textMessages.isNotEmpty()

    fun hasFileChunk(): Boolean = fileChunks.isNotEmpty()

    fun takeText(): SocketMessage.Text? = textMessages.poll()
    fun takeFileChunk(): SocketMessage.FileChunk? = fileChunks.poll()

    fun sendText(text: String) { textMessages.offer(SocketMessage.Text(text)) }

    @Throws(OutOfMemoryError::class)
    fun sendFileInChunks(fileId: String, file: File, chunkSize: Int) {
        file.inputStream().use { input ->
            val buffer = ByteArray(chunkSize)
            var totalRead = 0L
            val fileSize = file.length()

            while (true) {
                val read = input.read(buffer)
                if (read == -1) break

                totalRead += read
                val chunkData = buffer.copyOf(read)
                val isLastChunk = totalRead == fileSize
                fileChunks.offer(
                    SocketMessage.FileChunk(
                        fileId,
                        chunkData,
                        isLastChunk
                    )
                )
            }
        }
    }
}