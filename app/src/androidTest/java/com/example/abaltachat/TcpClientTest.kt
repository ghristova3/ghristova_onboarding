package com.example.abaltachat

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.domain.repository.ChatRepository
import com.example.abaltachat.network.ConnectionListener
import com.example.abaltachat.network.TcpClient
import com.example.abaltachat.network.TcpServer
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TcpClientTest {

    private lateinit var server: TcpServer
    private lateinit var client: TcpClient
    private lateinit var serverSideSocket: Socket
    private lateinit var serverScope: CoroutineScope
    private lateinit var clientScope: CoroutineScope

    private lateinit var fileReceived: File
    private val tempDir = Files.createTempDirectory("tcp-test").toFile()

    private val messageLatch = CountDownLatch(5)
    private val fileLatch = CountDownLatch(1)
    private val twoFilesLatch = CountDownLatch(2)
    private val errorLatch = CountDownLatch(1)
    private val progressUpdates = mutableListOf<Int>()

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionError(message: String?) {
            errorLatch.countDown()
        }

        override fun onFileIncoming(fileName: String, fileSize: Long) {}

        override fun onFileProgressUpdated(fileName: String, progress: Int) {
            progressUpdates.add(progress)
        }

        override fun onFileReceived(file: File) {
            fileReceived = file
            fileLatch.countDown()
            twoFilesLatch.countDown()
        }

        override fun onFileTransferError(fileName: String, ex: Exception?) {
            Log.d(TAG, "Transfer error for $fileName: ${ex?.message}")
            errorLatch.countDown()
        }
    }

    @Before
    fun setup() {
        serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        server = TcpServer(
            onClientConnected = { Log.d(TAG, "Server accepted connection from $it") },
            onMessageReceived = { msg ->
                if (msg is ChatMessage.TextMessage) messageLatch.countDown()
            },
            connectionListener = connectionListener,
            path = tempDir.absolutePath + File.separator
        )

        server.start(serverScope)

        runBlocking {
            delay(10)
            serverSideSocket = Socket("127.0.0.1", ChatRepository.PORT)

            client = TcpClient(
                socket = serverSideSocket,
                onMessageReceived = { msg ->
                    if (msg is ChatMessage.TextMessage) messageLatch.countDown()
                },
                connectionListener = connectionListener,
                path = tempDir.absolutePath + File.separator
            )
            client.start(clientScope)
        }
    }

    @Test
    fun testTransferLargeFileWithInterleavedMessages() = runBlocking {
        val testFileName = "large_test_file.txt"
        val testFile = File(tempDir, testFileName)
        FileOutputStream(testFile).use { output ->
            val chunk = ByteArray(1024 * 1024) { (it % 256).toByte() }
            repeat(3) { output.write(chunk) }
        }

        client.sendFile(testFile)
        repeat(5) { client.sendMessage("Text:Message #$it") }

        Assert.assertTrue(messageLatch.await(10, TimeUnit.SECONDS))
        Assert.assertTrue(fileLatch.await(30, TimeUnit.SECONDS))

        Assert.assertTrue(::fileReceived.isInitialized)
        Assert.assertEquals(testFile.length(), fileReceived.length())

        Assert.assertTrue(progressUpdates.any { it == 100 })
    }

    @Test
    fun testTransferTwoFiles() = runBlocking {
        val testFile = File(tempDir, "test_file_1.txt")
        FileOutputStream(testFile).use { output ->
            val chunk = ByteArray(1024 * 1024) { (it % 256).toByte() }
            repeat(2) { output.write(chunk) }
        }

        val testFile2 = File(tempDir, "test_file_2.txt")
        FileOutputStream(testFile2).use { output ->
            val chunk = ByteArray(1024 * 1024) { (it % 256).toByte() }
            repeat(3) { output.write(chunk) }
        }

        client.sendFile(testFile)
        client.sendFile(testFile2)

        Assert.assertTrue(twoFilesLatch.await(30, TimeUnit.SECONDS))
    }

    @Test
    fun testBrokenFileTransferTriggersError() = runBlocking {
        val fileName = "file.txt"

        val original = ByteArray(5 * 1024 * 1024) { it.toByte() }
        val broken = corruptBytes(original)

        val file = File(tempDir, fileName)
        val brokenFile = File(tempDir, "broken_$fileName")

        file.writeBytes(original)
        brokenFile.writeBytes(broken)

        client.sendFile(brokenFile)

        Assert.assertTrue(fileLatch.await(10, TimeUnit.SECONDS))
        Assert.assertNotEquals(file.length(), fileReceived.length())
    }

    private fun corruptBytes(data: ByteArray): ByteArray {
        val corrupted = data.copyOf()
        if (corrupted.isNotEmpty()) {
            for (i in 0 until 10) {
                val index = (corrupted.indices).random()
                corrupted[index] = (corrupted[index].toInt() xor 0xFF).toByte()
            }
        }
        return corrupted
    }

    @After
    fun tearDown() {
        client.stop()
        server.stop()
        clientScope.cancel()
        serverScope.cancel()
    }

    companion object {
        const val TAG = "TcpClientLargeFileTest"
    }
}
