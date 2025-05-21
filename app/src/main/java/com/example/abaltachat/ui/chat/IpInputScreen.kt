package com.example.abaltachat.ui.chat

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.abaltachat.R
import com.example.abaltachat.utils.getLocalIpAddress

@Composable
fun IpInputScreen(
    viewModel: ChatViewModel,
    padding: PaddingValues,
    onConnect: (String) -> Unit
) {
    // TODO Remove hardcoded IP address
    var ipText by remember { mutableStateOf("192.168.0.110") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(padding)
            .padding()
    ) {
        val ip = getLocalIpAddress() ?: "Unknown IP"
        Text("Server IP: $ip")

        Spacer(modifier = Modifier.padding(16.dp))

        TextField(
            value = ipText,
            onValueChange = { ipText = it },
            label = { Text(stringResource(R.string.enter_ip_address)) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onConnect(ipText) },
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
            enabled = ipText.isNotBlank()
        ) {
            Text(stringResource(R.string.connect))
        }
    }
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}