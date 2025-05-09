package com.example.abaltachat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.example.abaltachat.ui.chat.IpInputScreen
import com.example.abaltachat.ui.theme.AbaltaChatTheme

class ChatActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AbaltaChatTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = "Abalta chat") }
                        )
                    }
                ) { padding ->
                    IpInputScreen(padding) { ipAddress ->
                        // TODO Connect to IP $ipAddress
                    }
                }
            }
        }
    }
}
