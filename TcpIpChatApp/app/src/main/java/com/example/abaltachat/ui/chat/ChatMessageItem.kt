package com.example.abaltachat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.abaltachat.domain.model.ChatMessage

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val alignment = if (message.isIncoming) Alignment.Start else Alignment.End
    val background = if (message.isIncoming) Color.LightGray else MaterialTheme.colorScheme.primary
    val textColor = if (message.isIncoming) Color.Black else Color.White

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        when (message) {
            is ChatMessage.TextMessage -> {
                Text(
                    text = message.text,
                    modifier = Modifier
                        .padding(4.dp)
                        .background(background, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    style = TextStyle(color = textColor)
                )
            }

            is ChatMessage.FileMessage -> {
                Column(
                    modifier = Modifier
                        .padding(4.dp)
                        .background(background, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "📁 ${message.file.name} (${message.file.length()}) bytes)",
                        style = TextStyle(color = textColor)
                    )
                    if (message.transferProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = message.transferProgress / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
