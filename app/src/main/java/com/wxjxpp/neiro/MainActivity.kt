package com.wxjxpp.neiro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wxjxpp.neiro.app.MusicPlayerApp
import com.wxjxpp.neiro.app.MusicPlayerApplication
import com.wxjxpp.neiro.ui.theme.MusicPlayerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MusicPlayerApplication).container
        setContent {
            MusicPlayerTheme {
                MusicPlayerApp(container = container)
            }
        }
    }
}