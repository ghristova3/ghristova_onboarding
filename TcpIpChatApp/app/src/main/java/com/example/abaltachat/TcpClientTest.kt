package com.example.abaltachat

import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.domain.repository.ChatRepository
import com.example.abaltachat.network.ConnectionListener
import com.example.abaltachat.network.TcpClient
import com.example.abaltachat.network.TcpServer
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket
import java.util.concurrent.CountDownLatch

const val TEST_SUCCESS = "TEST SUCCEED:"
const val TEST_FAILED = "TEST FAILED:"
val tempDir = File("test_files").apply { mkdirs() }
var messageLatch = CountDownLatch(100)
var fileLatch = CountDownLatch(2)
var errorLatch = CountDownLatch(1)

fun main() = runBlocking {

    val scope = CoroutineScope(Dispatchers.IO)

    val file500MB = File(tempDir, "test_500MB.bin").apply {
        if (!exists()) {
            println("Creating 500MB file")
            outputStream().use { it.write(ByteArray(500 * 1024 * 1024)) }
        }
    }

    val file1GB = File(tempDir, "test_1GB.bin").apply {
        if (!exists()) {
            println("Creating 1GB file")
            outputStream().use { it.write(ByteArray(1024 * 1024 * 1024)) }
        }
    }

    val file5MB = File(tempDir, "test_5MB.bin").apply {
        if (!exists()) {
            println("Creating 500MB file")
            outputStream().use { it.write(ByteArray(5 * 1024 * 1024)) }
        }
    }

    println("Server waiting for client...")
    var server: TcpServer? = null
    scope.launch {
        server = TcpServer(
            onClientConnected = { },
            connectionListener = object : ConnectionListener {
                override fun onConnectionError(error: String?) {
                    println("Server Connection error: $error")
                }

                override fun onFileIncoming(fileName: String, fileSize: Long) {
                    println("Incoming file: $fileName ($fileSize bytes)")
                }

                override fun onFileProgressUpdated(fileName: String, progress: Int) {
                    println("File $fileName progress: $progress%")
                }

                override fun onFileReceived(file: File) {
                    println("File received: ${file.name}, size=${file.length()}")
                    fileLatch.countDown()
                }

                override fun onFileTransferError(fileName: String, exception: Exception?) {
                    println("File transfer error for $fileName: ${exception?.message}")
                    errorLatch.countDown()
                }
            },
            onMessageReceived = {
                println("Server received message: ${(it as? ChatMessage.TextMessage)?.text}")
                if (it is ChatMessage.TextMessage) messageLatch.countDown()
            },
            path = tempDir.absolutePath
        )
        server?.start(scope)
    }

    delay(1000)

    val socket = Socket("127.0.0.1", ChatRepository.PORT)
    val client = TcpClient(
        socket = socket,
        connectionListener = object : ConnectionListener {
            override fun onConnectionError(message: String?) {
                println("Client Connection error: $message")
            }

            override fun onFileIncoming(fileName: String, fileSize: Long) {}
            override fun onFileProgressUpdated(fileName: String, progress: Int) {
                println("Client file progress: $progress%")
            }

            override fun onFileReceived(file: File) { }

            override fun onFileTransferError(fileName: String, ex: Exception?) {
                println("Client File transfer error for $fileName: ${ex?.message}")
            }
        },
        onMessageReceived = {},
    )
    client.start(scope)

    // Tests
    testTransferLargeFileWithInterleavedMessages(client, file500MB, file1GB)
    testBrokenFileTransferTriggersError(client, file5MB)

    println("Stopping client")

    println("Done.")
}

private suspend fun testTransferLargeFileWithInterleavedMessages(
    client: TcpClient,
    file500MB: File,
    file1GB: File,
) {
    messageLatch = CountDownLatch(100)
    fileLatch = CountDownLatch(2)

    println("Sending 500MB file")
    client.sendFile(file500MB)

    println("Sending messages...")
    repeat(100) { i ->
        client.sendMessage("Message #$i")
        delay(100)
    }

    println("Sending 1GB file")
    client.sendFile(file1GB)

    delay(30000)

    if (fileLatch.count.toInt() == 0) {
        println("$TEST_SUCCESS Files received")
    } else {
        println("$TEST_FAILED Files NOT received")
    }

    if (messageLatch.count.toInt() == 0) {
        println("$TEST_SUCCESS Messages received")
    } else {
        println("$TEST_FAILED Messages NOT received")
    }
}

private suspend fun testBrokenFileTransferTriggersError(client: TcpClient, originalFile: File) {
    println("Sending broken file")
    val oneMB = 1024 * 1024
    val trimmed = originalFile.readBytes().copyOf((originalFile.length()- oneMB).toInt())

    val brokenFile = File(tempDir, "broken_${originalFile.name}").apply {
        outputStream().use { it.write(trimmed) }
    }

    client.sendFile(brokenFile)

    delay(3000)

    if (originalFile.length() != brokenFile.length()) {
        println("$TEST_SUCCESS File received with broken chuncks")
    } else {
        println("$TEST_FAILED File received")
    }
}


