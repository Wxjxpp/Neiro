package com.wxjxpp.neiro.core.player

import com.wxjxpp.neiro.core.model.PlaybackState
import com.wxjxpp.neiro.core.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * 播放控制契约。
 *
 * UI 只依赖这个接口，不依赖任何具体播放引擎。
 * 后续接 Media3 时新增 `Media3PlayerController : PlayerController`，
 * 在依赖装配处替换即可，界面代码零改动。
 *
 * "一起听"也走这一层：远端指令最终翻译成这里的调用。
 */
interface PlayerController {
    val state: StateFlow<PlaybackState>
    val queue: StateFlow<List<Song>>

    /** 拔出耳机（含蓝牙断开）时是否自动暂停。 */
    var pauseOnHeadphoneDisconnect: Boolean
    /** 其他应用抢占音频焦点时是否暂停。 */
    var pauseOnAudioFocusLoss: Boolean


    fun setQueue(songs: List<Song>, startIndex: Int = 0, autoPlay: Boolean = false)
    fun play(song: Song)
    fun togglePlay()
    fun pause()
    fun resume()
    fun next()
    fun previous()

    /** 按毫秒定位。UI 上的百分比进度由调用方换算，避免接口出现两种语义。 */
    fun seekTo(positionMs: Long)

    fun toggleShuffle()
    fun cycleRepeatMode()
    fun setSpeed(speed: Float)
    fun setVolume(volume: Float)
    /** [实验室] 8-bit 播放模式。 */
    fun setEightBitMode(enabled: Boolean)
    /** [实验室] 80 倍速播放模式。 */
    fun setTurboSpeedMode(enabled: Boolean)

    /** 追加到队列尾部。 */
    fun addToQueue(songs: List<Song>)

    /** 插入到当前歌曲之后（"下一首播放"）。 */
    fun playNext(songs: List<Song>)

    fun removeFromQueue(songId: String)
    fun clearQueue()
    fun release()
}