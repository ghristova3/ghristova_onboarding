package com.example.abaltachat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.domain.repository.ChatRepository
import com.example.abaltachat.network.ConnectionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ChatViewModel(fileDir: String) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _fileTransferProgress = MutableStateFlow<Int?>(null)
    val fileTransferProgress: StateFlow<Int?> = _fileTransferProgress.asStateFlow()

    private val progressListener = object : ConnectionListener {
        override fun onConnectionError(message: String?) {
            val errorMessage = "Connection failed: $message}"
            addMessage(ChatMessage.TextMessage(errorMessage, isIncoming = true))

            viewModelScope.launch(Dispatchers.Main) {
                _toastMessage.emit(errorMessage)
            }
        }

        override fun onFileIncoming(fileName: String, fileSize: Long) {
            addMessage(ChatMessage.TextMessage("Receiving file: $fileName Size: $fileSize", isIncoming = false))
        }

        override fun onFileProgressUpdated(fileName: String, progress: Int) {
            _fileTransferProgress.value = progress
        }

        override fun onFileReceived(file: File) {
            _fileTransferProgress.value = null
            addMessage(ChatMessage.TextMessage("Completed file: ${file.name} Size: ${file.length()}", isIncoming = false))
        }

        override fun onFileTransferError(fileName: String, ex: Exception?) {
            _fileTransferProgress.value = null
            addMessage(ChatMessage.TextMessage("Error receiving file: $fileName $ex", isIncoming = false))
        }
    }

    private val repository = ChatRepository(
        path = fileDir,
        onClientConnected = { clientIp ->
            _isConnected.value = true
            addMessage(ChatMessage.TextMessage("Connected to $clientIp", isIncoming = true))
        },
        onMessageReceived = { message ->
            addMessage(message)
        },
        scope = viewModelScope,
        connectionListener = progressListener
    )

    init {
        repository.startServer()
    }

    fun connectTo(ipAddress: String) {
        viewModelScope.launch {
            val result = repository.connectTo(ipAddress)
            if (result.isSuccess) {
                _isConnected.value = true
                addMessage(ChatMessage.TextMessage("Connected to $ipAddress", isIncoming = true))
            } else {
                val errorMessage = "Connection failed: ${result.exceptionOrNull()?.message}"
                addMessage(ChatMessage.TextMessage(errorMessage, isIncoming = true))
                _toastMessage.emit(errorMessage)
            }
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return

        repository.sendMessage(text)
        addMessage(ChatMessage.TextMessage(text, isIncoming = false))
    }

    fun sendFile(file: File) {
        viewModelScope.launch {
            try {
                repository.sendFile(file)
                addMessage(ChatMessage.TextMessage("Sent file: ${file.name}", isIncoming = false))
            } catch (ex: Exception) {
                val errorMessage = "Error sending file: ${ex.message}"
                addMessage(ChatMessage.TextMessage(errorMessage, isIncoming = true))
                _toastMessage.emit(errorMessage)
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value += message
    }


    override fun onCleared() {
        super.onCleared()
        repository.stopServer()
    }

    // TODO Use SAF because of Android 11+
//    fun saveIncomingFile(outputStream: OutputStream) {
//        viewModelScope.launch(Dispatchers.IO) {
//            val state = _incomingFileState.value ?: return@launch
//
//            val buffer = ByteArray(4096)
//            var remaining = state.fileSize
//            val input = state.inputStream
//
//            var totalRead = 0L
//
//            try {
//                while (remaining > 0) {
//                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
//                    if (read == -1) {
//                        Log.e(TAG, "Unexpected EOF — file transfer incomplete")
//                        break
//                    }
//
//                    outputStream.write(buffer, 0, read)
//                    totalRead += read
//                    remaining -= read
//
//                    val progress = ((totalRead * 100) / state.fileSize).toInt()
//                    Log.d(TAG, "Progress: $progress%")
//                }
//
//                outputStream.flush()
//                Log.d(TAG, "File received complete: $totalRead / ${state.fileSize} bytes")
//
//            } catch (e: IOException) {
//                Log.e(TAG, "File saving failed: ${e.message}", e)
//            } finally {
//                outputStream.close()
//                withContext(Dispatchers.Main) {
//                    _fileTransferProgress.value = null
//                    _incomingFileState.value = null
//                }
//            }
//        }
//    }

    companion object {
        const val TAG = "ChatViewModel"
    }
}