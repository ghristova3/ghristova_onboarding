package com.example.abaltachat.ui.chat

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(viewModel: ChatViewModel, onPickFile: () -> Unit, padding: PaddingValues) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var currentMessage by remember { mutableStateOf("") }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp)) {

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
                Icon(Icons.Default.Create, contentDescription = "Send File")
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
}
