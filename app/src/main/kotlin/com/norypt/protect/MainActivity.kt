package com.norypt.protect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.norypt.protect.ui.theme.NoryptColors
import com.norypt.protect.ui.theme.NoryptProtectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoryptProtectTheme {
                Box(Modifier.fillMaxSize().background(NoryptColors.Bg), contentAlignment = Alignment.Center) {
                    Text("Norypt Protect", color = NoryptColors.Text)
                }
            }
        }
    }
}
