package com.example.abaltachat.network

import java.io.File

interface ConnectionListener {

    fun onConnectionError(message: String?)

    fun onFileIncoming(fileName: String, fileSize: Long)

    fun onFileProgressUpdated(fileName: String, progress: Int)

    fun onFileReceived(file: File)

    fun onFileTransferError(fileName: String, ex: Exception?)
}