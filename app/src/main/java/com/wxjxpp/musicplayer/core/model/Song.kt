package com.wxjxpp.musicplayer.core.model

import androidx.compose.ui.graphics.Color

/**
 * 领域模型。后续接入真实音源时只替换数据来源，UI 不需要改。
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** 占位封面色；接入网络后换成 coverUrl。 */
    val coverSeed: Color,
)

enum class RepeatMode { Off, All, One }

data class PlaybackState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val repeatMode: RepeatMode = RepeatMode.All,
    val shuffle: Boolean = false,
)