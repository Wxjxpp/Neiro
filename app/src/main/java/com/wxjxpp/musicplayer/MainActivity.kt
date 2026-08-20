package com.wxjxpp.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wxjxpp.musicplayer.app.MusicPlayerApp
import com.wxjxpp.musicplayer.app.MusicPlayerApplication
import com.wxjxpp.musicplayer.ui.theme.MusicPlayerTheme

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