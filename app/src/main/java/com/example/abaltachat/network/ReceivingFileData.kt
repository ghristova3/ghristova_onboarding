package com.example.abaltachat.network

import java.io.File

data class ReceivingFileData(
    val file: File,
    val size: Long,
    var totalReceived: Long = 0L
)