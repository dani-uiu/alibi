package com.alibi.spoofer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alibi.spoofer.ui.SpooferScreen
import com.alibi.spoofer.ui.theme.AlibiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlibiTheme {
                SpooferScreen()
            }
        }
    }
}
