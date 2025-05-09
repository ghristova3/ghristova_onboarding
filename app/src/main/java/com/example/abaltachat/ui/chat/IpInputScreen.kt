package com.example.abaltachat.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.abaltachat.R

@Composable
fun IpInputScreen(
    padding: PaddingValues,
    onConnect: (String) -> Unit
) {
    var ipText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(padding)
            .padding()
    ) {
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
}