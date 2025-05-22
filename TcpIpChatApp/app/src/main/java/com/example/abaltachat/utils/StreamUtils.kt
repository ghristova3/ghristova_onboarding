package com.example.abaltachat.utils

import java.io.InputStream

fun InputStream.readFullyOrNull(buffer: ByteArray): Int {
    var bytesRead = 0
    while (bytesRead < buffer.size) {
        val read = this.read(buffer, bytesRead, buffer.size - bytesRead)
        if (read == -1) return -1
        bytesRead += read
    }
    return bytesRead
}