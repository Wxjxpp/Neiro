package com.wxjxpp.musicplayer.core.model

/**
 * 播放状态与播放队列模型。
 */

enum class RepeatMode { Off, All, One }

data class PlaybackState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.All,
    val shuffle: Boolean = false,
    val speed: Float = 1f,
    val volume: Float = 1f,
) {
    /** 0f..1f，供进度条使用。 */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}