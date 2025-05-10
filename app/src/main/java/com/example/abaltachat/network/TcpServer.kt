package com.example.abaltachat.network

import android.util.Log
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ServerSocket

class TcpServer(
    private val onClientConnected: (clientAddress: String) -> Unit,
    private val onMessageReceived: (ChatMessage) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var running = true
    private var serverJob: Job? = null
    private var connectedClient: TcpClient? = null

    fun start(scope: CoroutineScope) {
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(ChatRepository.PORT)
                Log.d(TAG, "$LISTENING_ON_PORT: ${ChatRepository.PORT}")
                while (running) {
                    val socket = serverSocket?.accept() ?: continue
                    val clientAddress = socket.inetAddress.hostAddress
                    Log.d(TAG, "$CLIENT_CONNECTED: $clientAddress")
                    onClientConnected(clientAddress)

                    val tcpClient = TcpClient(socket, onMessageReceived)
                    tcpClient.start(scope)
                    connectedClient = tcpClient
                }
            } catch (e: IOException) {
                Log.e(TAG, "$SERVER_ERROR: ${e.message}", e)
            }
        }
    }

    fun sendMessage(message: String) {
        try {
            connectedClient?.sendMessage(message)
        } catch (e: IOException) {
            Log.e(TAG, "$ERROR_SENDING: ${e.message}", e)
        }
    }

    fun stop() {
        running = false
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "$ERROR_CLOSING_SERVER_SOCKET: ${e.message}", e)
        }
    }

    companion object {
        const val TAG = "TcpServer"
        const val ERROR_SENDING = "Error sending message"
        const val CLIENT_CONNECTED = "Client connected"
        const val SERVER_ERROR = "Server error"
        const val LISTENING_ON_PORT = "Listening on port"
        const val ERROR_CLOSING_SERVER_SOCKET = "Error closing server socket"
    }
}
