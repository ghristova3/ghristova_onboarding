package com.example.abaltachat.network

import android.util.Log
import com.example.abaltachat.domain.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.net.Socket

class TcpClient(
    private val socket: Socket,
    private val onMessageReceived: (ChatMessage) -> Unit
) {
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val input = DataInputStream(socket.getInputStream())
                while (true) {
                    val header = input.readUTF()
                    if (header.startsWith("MSG:")) {
                        val msg = header.removePrefix("MSG:")
                        withContext(Dispatchers.Main) {
                            onMessageReceived(ChatMessage.TextMessage(msg, isIncoming = true))
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e("TcpClient", "Error: ${ex.message}", ex)
            }
        }
    }
}