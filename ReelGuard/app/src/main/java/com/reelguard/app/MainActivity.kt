package com.reelguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.reelguard.app.ui.ReelGuardApp
import com.reelguard.app.ui.theme.ReelGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReelGuardTheme {
                ReelGuardApp()
            }
        }
    }
}
