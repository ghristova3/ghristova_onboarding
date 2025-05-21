package com.example.abaltachat

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import com.example.abaltachat.ui.chat.ChatScreen
import com.example.abaltachat.ui.chat.ChatViewModel
import com.example.abaltachat.ui.chat.IpInputScreen
import com.example.abaltachat.ui.theme.AbaltaChatTheme
import java.io.File

class ChatActivity : ComponentActivity() {

    private lateinit var viewModel: ChatViewModel

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // TODO Use Store Access framework because of Android 11+
        val fileDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/"
//            applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + File.separator
        viewModel = ChatViewModel(fileDir)

        Log.d("Path", "Path: ${fileDir}")

        setContent {
            val isConnected by viewModel.isConnected.collectAsState(false)
            AbaltaChatTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = "Abalta chat") }
                        )
                    }
                ) { padding ->

                    if (!isConnected) {
                        IpInputScreen(viewModel, padding) { ip ->
                            viewModel.connectTo(ip)
                        }
                    } else {
                        ChatScreen(
                            padding = padding,
                            viewModel = viewModel,
                            onPickFile = { launchFilePicker() }
                        )
                    }
                }
            }
        }
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerResultLauncher.launch(intent)
    }

    private val filePickerResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    val file = getFileFromUri(uri)
                    file?.let { viewModel.sendFile(it) }
                }
            }
        }

    private fun getFileFromUri(uri: Uri): File? {
        val fileName = getFileName(uri) ?: return null
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val tempFile = File(cacheDir, fileName)
            tempFile.outputStream().use { outputStream ->
                inputStream?.copyTo(outputStream)
            }
            tempFile
        } catch (ex: Exception) {
            Log.e(TAG, "Error getting file from URI: ${ex.message}", ex)
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    companion object {
        const val TAG = "ChatActivity"
    }
}
