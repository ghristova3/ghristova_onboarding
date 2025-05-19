import android.util.Log
import com.example.abaltachat.domain.model.ChatMessage
import com.example.abaltachat.domain.repository.ChatRepository
import com.example.abaltachat.network.FileTransferProgressListener
import com.example.abaltachat.network.TcpClient
import com.example.abaltachat.network.TcpServer
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch


class TcpClientTest {

    private lateinit var server: TcpServer
    private lateinit var client: TcpClient
    private lateinit var serverSideSocket: Socket
    private lateinit var serverScope: CoroutineScope
    private lateinit var clientScope: CoroutineScope

    private lateinit var fileReceived: File
    private val tempDir = Files.createTempDirectory("tcp-test").toFile()

    private val messageLatch = CountDownLatch(1000)
    private val fileLatch = CountDownLatch(1)
    private val errorLatch = CountDownLatch(1)
    private val progressUpdates = mutableListOf<Int>()

    @Before
    fun setup() {
        serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        server = TcpServer(
            onClientConnected = { Log.d(TAG, "Server accepted connection from $it") },
            onMessageReceived = { msg ->
                Log.d(TAG, "Server onMessageReceived $msg")
                if (msg is ChatMessage.TextMessage) messageLatch.countDown()
            },
            fileTransferProgressListener = object : FileTransferProgressListener {
                override fun onReceivingFile(fileName: String, fileSize: Long) {}
                override fun onProgressUpdate(progress: Int) {
                    progressUpdates.add(progress)
                }

                override fun onFileReceived(fileName: String, fileSize: Long) {
                    fileReceived = File(tempDir, fileName)
                    fileLatch.countDown()
                }

                override fun onTransferError(fileName: String, ex: Exception) {
                    Log.d(TAG, "Server Transfer error for $fileName: ${ex.message}")
                    errorLatch.countDown()
                }
            },
            path = tempDir.absolutePath + File.separator
        )

        server.start(serverScope)

        runBlocking {
            delay(500)
            serverSideSocket = Socket("127.0.0.1", ChatRepository.PORT)
            client = TcpClient(
                socket = serverSideSocket,
                onMessageReceived = { msg ->
                    Log.d("TAG", "Client onMessageReceived $msg")
                    if (msg is ChatMessage.TextMessage) messageLatch.countDown()
                },
                fileTransferProgressListener = object : FileTransferProgressListener {
                    override fun onReceivingFile(fileName: String, fileSize: Long) {}
                    override fun onProgressUpdate(progress: Int) {
                        progressUpdates.add(progress)
                    }

                    override fun onFileReceived(fileName: String, fileSize: Long) {
                        fileReceived = File(tempDir, fileName)
                        fileLatch.countDown()
                    }

                    override fun onTransferError(fileName: String, ex: Exception) {
                        Log.d(TAG, "Client Transfer error for $fileName: ${ex.message}")
                        errorLatch.countDown()
                    }
                },
                path = tempDir.absolutePath + File.separator
            )
            client.start(clientScope)
        }
    }

    @Test
    fun testLargeFileTransferWithInterleavedMessages() = runBlocking {
        val testFileName = "large_test_file.txt"
        val testFile = File(tempDir, testFileName).apply {
            writeBytes(ByteArray(20 * 1024 * 1024) { (it % 256).toByte() })
        }

        client.sendFile(testFile)

        repeat(1000) {
            client.sendMessage("Text:Message #$it")
        }

        Assert.assertTrue(messageLatch.await(10, java.util.concurrent.TimeUnit.SECONDS))
        Assert.assertTrue(fileLatch.await(30, java.util.concurrent.TimeUnit.SECONDS))

        Assert.assertTrue(::fileReceived.isInitialized)
        Assert.assertEquals(testFile.length(), fileReceived.length())

        Assert.assertTrue(progressUpdates.any { it == 100 })
    }

    @Test
    fun testBrokenFileTransferTriggersError() = runBlocking {
        val output = DataOutputStream(BufferedOutputStream(serverSideSocket.getOutputStream()))

        val testFileName = "broken_file.txt"
        val fileSize = 1024
        val header = "File:$testFileName:$fileSize".toByteArray()
        output.writeByte(TcpClient.CONTROL_MESSAGE_TYPE.toInt())
        output.writeInt(header.size)
        output.write(header)
        output.flush()

        val halfSize = (fileSize / 2)
        output.writeByte(TcpClient.CONTROL_FILE_CHUNK.toInt())
        output.writeInt(fileSize)
        output.write(ByteArray(halfSize) { 0x55 })
        output.flush()

        Assert.assertTrue(errorLatch.await(10, java.util.concurrent.TimeUnit.SECONDS))
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
