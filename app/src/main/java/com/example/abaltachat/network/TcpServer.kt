package com.example.abaltachat.network

import android.util.Log
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.BindException
import java.net.ServerSocket
import java.net.SocketException

class TcpServer(
    private val onClientConnected: (clientAddress: String) -> Unit,
    private val onMessageReceived: (ChatMessage) -> Unit,
    private val connectionListener: ConnectionListener,
    private val path: String? = null,
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var connectedClient: TcpClient? = null

    fun start(scope: CoroutineScope) {
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(ChatRepository.PORT)
                Log.d(TAG, "$LISTENING_ON_PORT: ${ChatRepository.PORT}")
                while (isActive) {
                    val socket = serverSocket?.accept() ?: continue
                    val clientAddress = socket.inetAddress.hostAddress
                    Log.d(TAG, "$CLIENT_CONNECTED: $clientAddress")
                    onClientConnected(clientAddress)

                    val tcpClient = TcpClient(
                        socket,
                        onMessageReceived,
                        connectionListener,
                        path
                    )
                    tcpClient.start(scope)
                    connectedClient = tcpClient
                }
            } catch (ex: IOException) {
                Log.e(TAG, "$SERVER_ERROR: ${ex.message}", ex)
                connectionListener.onConnectionError(ex.message)
            } catch (ex: SocketException) {
                Log.e(TAG, "$SERVER_ERROR: ${ex.message}", ex)
                connectionListener.onConnectionError(ex.message)
            } catch (ex: BindException) {
                Log.e(TAG, "$SERVER_ERROR: ${ex.message}", ex)
                connectionListener.onConnectionError(ex.message)
            }
        }
    }

    fun sendFile(file: File) {
        try {
            connectedClient?.sendFile(file)
        } catch (e: IOException) {
            Log.e(TAG, "$ERROR_SENDING_FILE: ${e.message}", e)
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
        const val ERROR_SENDING_FILE = "Error sending file"
        const val ERROR_RECEIVING_FILE = "Error receiving file"
    }
}
