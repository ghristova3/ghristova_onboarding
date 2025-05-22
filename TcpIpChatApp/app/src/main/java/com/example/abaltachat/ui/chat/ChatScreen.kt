package com.example.abaltachat.ui.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(viewModel: ChatViewModel, onPickFile: () -> Unit, padding: PaddingValues) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val progress by viewModel.fileTransferProgress.collectAsState()
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var currentMessage by remember { mutableStateOf("") }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp)) {

        if (progress != null) {
            LinearProgressIndicator(
                progress = progress!! / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(padding)
                .fillMaxWidth(),
            state = scrollState,
            reverseLayout = true
        ) {
            items(messages.reversed()) { message ->
                ChatMessageItem(message)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = currentMessage,
                onValueChange = { currentMessage = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message") }
            )
            IconButton(onClick = {
                viewModel.sendText(currentMessage)
                currentMessage = ""
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
            IconButton(onClick = onPickFile) {
                Icon(Icons.Default.Add, contentDescription = "Send File")
            }
        }
    }

    LaunchedEffect(messages.size) {
        coroutineScope.launch {
            scrollState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // TODO Show file picker to save file
//        AlertDialog(
//            onDismissRequest = {  },
//            confirmButton = {
//                TextButton(onClick = {
//                    viewModel.dismissIncomingFile()
//                    filePickerLauncher.launch(state.fileName) // use ActivityResultLauncher
//                }) {
//                    Text("Download")
//                }
//            },
//            dismissButton = {
//                TextButton(onClick = { viewModel.dismissIncomingFile() }) {
//                    Text("Cancel")
//                }
//            },
//            title = { Text("Incoming File") },
//            text = { Text("File: ${state.fileName}\nSize: ${state.fileSize} bytes") }
//        )
//    }
}
