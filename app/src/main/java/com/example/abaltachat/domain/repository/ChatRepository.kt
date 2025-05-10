package com.example.abaltachat.domain.repository

import android.util.Log
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.network.TcpClient
import com.example.abaltachat.network.TcpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Socket

class ChatRepository(
    private val onClientConnected: (clientAddress: String) -> Unit,
    private val onMessageReceived: (ChatMessage) -> Unit,
    private val scope: CoroutineScope
) {
    private var server: TcpServer? = null
    private var clientConnection: TcpClient? = null

    fun startServer() {
        server = TcpServer(onClientConnected, onMessageReceived)
        server?.start(scope)
    }

    suspend fun connectTo(ip: String): Result<Unit> {
        val async = scope.async(Dispatchers.IO) {
            try {
                val socket = Socket(ip, PORT)
                clientConnection = TcpClient(socket, onMessageReceived).also {
                    it.start(scope)
                }
                Result.success(Unit)
            } catch (ex: IOException) {
                Log.e(TAG, "$CONNECT_FAILED${ex.message}", ex)
                Result.failure(ex)
            }
        }
        return async.await()
    }

    fun sendMessage(message: String) {
        scope.launch(Dispatchers.IO) {
            server?.sendMessage(message) // will work only if acting as server
            clientConnection?.sendMessage(message) // will work only if acting as client
        }
    }

    fun stopServer() {
        server?.stop()
        clientConnection?.stop()
    }

    companion object {
        const val TAG = "ChatRepository"
        const val CONNECT_FAILED = "Connect failed: "
        const val PORT = 6000
    }
}