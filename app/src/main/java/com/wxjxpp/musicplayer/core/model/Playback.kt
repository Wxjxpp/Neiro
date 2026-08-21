package com.wxjxpp.musicplayer.core.model

/**
 * 播放状态与播放队列模型。
 */

enum class RepeatMode { Off, All, One }

/**
 * 随机策略。
 * True   = 真随机，每次独立掷骰，可能连续重复同一首
 * Pseudo = 伪随机，一轮内不重复，播完一轮再洗牌
 */
enum class ShuffleMode { Pseudo, True }

data class PlaybackState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.All,
    val shuffle: Boolean = false,
    val shuffleMode: ShuffleMode = ShuffleMode.Pseudo,
    val speed: Float = 1f,
    val volume: Float = 1f,
) {
    /** 0f..1f，供进度条使用。 */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}