package com.example.abaltachat.network

import android.util.Log
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.ServerSocket

class TcpServer(
    private val onMessageReceived: (ChatMessage) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var running = true
    private var serverJob: Job? = null

    fun start(scope: CoroutineScope) {
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(ChatRepository.PORT,0, InetAddress.getByName("127.0.0.1"))
                Log.d("TcpServer", "Server started on port ${ChatRepository.PORT}")
                while (running) {
                    val clientSocket = serverSocket?.accept() ?: continue
                    Log.d("TcpServer", "Client connected: ${clientSocket.inetAddress.hostAddress}")
                    TcpClient(clientSocket, onMessageReceived).start(this)
                }
            } catch (ex: Exception) {
                Log.e("TcpServer", "Error: ${ex.message}", ex)
            }
        }
    }

    fun stop() {
        running = false
        serverJob?.cancel()
        serverSocket?.close()
    }
}
