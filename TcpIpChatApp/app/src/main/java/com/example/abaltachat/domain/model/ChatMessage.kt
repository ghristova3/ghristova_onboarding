package com.example.abaltachat.domain.model

import java.io.File

sealed class ChatMessage {
    abstract val isIncoming: Boolean
    abstract val timestamp: Long

    data class TextMessage(
        val text: String,
        override val isIncoming: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()

    data class FileMessage(
        val file: File,
        override val isIncoming: Boolean,
        val transferProgress: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()
}