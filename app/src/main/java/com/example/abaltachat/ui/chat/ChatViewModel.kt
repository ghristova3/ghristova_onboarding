package com.example.abaltachat.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.domain.repository.ChatRepository
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isConnected = MutableLiveData(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val repository = ChatRepository(onMessageReceived = { message ->
        addMessage(message)
    }, scope = viewModelScope)

    init {
        repository.startServer()
    }

    fun connectTo(ipAddress: String) {
        viewModelScope.launch {
            repository.connectTo(ipAddress)
            _isConnected.value = true // only if no exception occurred
            addMessage(ChatMessage.TextMessage("Connected to $ipAddress", isIncoming = true))
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return

        repository.sendMessage(text)
        addMessage(ChatMessage.TextMessage(text, isIncoming = false))
    }

    private fun addMessage(message: ChatMessage) {
        _messages.postValue(_messages.value?.plus(message))
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopServer()
    }

}