package com.example.abaltachat.network

interface FileTransferProgressListener {
    fun onReceivingFile(fileName: String, fileSize: Long)
    fun onProgressUpdate(progress: Int)
    fun onFileReceived(fileName: String, fileSize: Long)
    fun onTransferError(fileName: String, ex: Exception)
}