package com.example.abaltachat.domain.repository

import android.util.Log
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.network.TcpClient
import com.example.abaltachat.network.TcpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.net.Socket

class ChatRepository(
    private val onMessageReceived: (ChatMessage) -> Unit,
    private val scope: CoroutineScope
) {
    private var server: TcpServer? = null
    private var clientSocket: Socket? = null
    private var outputStream: DataOutputStream? = null

    fun startServer() {
        server = TcpServer(onMessageReceived)
        server?.start(scope)
    }

    fun connectTo(ip: String) {
        scope.launch(Dispatchers.IO) {
            try {
                delay(500) // TODO: Refactor it. Give server time to start
                clientSocket = Socket(ip, PORT)
                outputStream = DataOutputStream(clientSocket!!.getOutputStream())
                TcpClient(clientSocket!!, onMessageReceived).start(scope)
            } catch (ex: Exception) {
                Log.e("ChatRepository", "Connection error: ${ex.message}", ex)
            }
        }
    }

    fun sendMessage(text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                outputStream?.writeUTF("MSG:$text")
                outputStream?.flush()
            } catch (ex: Exception) {
                Log.e("ChatRepository", "Send error: ${ex.message}", ex)
            }
        }
    }

    fun stopServer() {
        server?.stop()
    }

    companion object {
        const val PORT = 6000
    }
}
