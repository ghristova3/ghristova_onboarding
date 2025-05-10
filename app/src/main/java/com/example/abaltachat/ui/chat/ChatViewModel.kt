package com.example.abaltachat.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>() // for one-time events
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val repository = ChatRepository(
        onClientConnected = { clientIp ->
            _isConnected.value = true
            addMessage(ChatMessage.TextMessage("Connected to $clientIp", isIncoming = true))
        },
        onMessageReceived = { message ->
            addMessage(message)
        },
        scope = viewModelScope
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

    private fun addMessage(message: ChatMessage) {
        _messages.value += message
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopServer()
    }
}