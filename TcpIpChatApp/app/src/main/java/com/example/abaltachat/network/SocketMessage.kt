package com.example.abaltachat.network

sealed class SocketMessage {

    data class Text(val text: String) : SocketMessage()

    data class FileChunk(
        val fileID: String,
        val data: ByteArray,
        val isLast: Boolean
    ) : SocketMessage()
}
