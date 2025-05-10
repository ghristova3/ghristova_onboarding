package com.example.abaltachat.network

import android.util.Log
import com.example.abaltachat.domain.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Socket

class TcpClient(
    private val socket: Socket,
    private val onMessageReceived: (ChatMessage) -> Unit
) {
    private var readJob: Job? = null

    fun start(scope: CoroutineScope) {
        readJob = scope.launch(Dispatchers.IO) {
            try {
                val reader = socket.getInputStream().bufferedReader()
                while (true) {
                    val line = reader.readLine() ?: break
                    onMessageReceived(ChatMessage.TextMessage(line, isIncoming = true))
                }
            } catch (e: IOException) {
                Log.e(TAG, "$ERROR_READING: ${e.message}", e)
            }
        }
    }

    fun sendMessage(message: String) {
        try {
            val writer = socket.getOutputStream().bufferedWriter()
            writer.write(message)
            writer.newLine()
            writer.flush()

            Log.d(TAG, "$SENT_MESSAGE$message")
        } catch (e: IOException) {
            Log.e(TAG, "$ERROR_SENDING: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            readJob?.cancel()
            socket.close()
        } catch (e: IOException) {
            Log.e(TAG, "$ERROR_CLOSING_SOCKET_ON_STOP: ${e.message}", e)
        }
    }

    companion object {
        const val TAG = "TcpClient"
        const val ERROR_READING = "Error reading from socket"
        const val ERROR_SENDING = "Send failed"
        const val ERROR_CLOSING_SOCKET_ON_STOP = "Error closing socket on stop"
        const val SENT_MESSAGE = "Sent: "
    }
}
