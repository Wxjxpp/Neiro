package com.wxjxpp.musicplayer.core.player

import com.wxjxpp.musicplayer.core.model.PlaybackState
import com.wxjxpp.musicplayer.core.model.RepeatMode
import com.wxjxpp.musicplayer.core.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 播放控制抽象。
 *
 * 现在是内存假实现，后续接 Media3 时新增 Media3PlayerController 实现该接口即可，
 * UI 层完全不需要改动。
 */
interface PlayerController {
    val state: StateFlow<PlaybackState>
    val queue: StateFlow<List<Song>>

    fun setQueue(songs: List<Song>)
    fun play(song: Song)
    fun togglePlay()
    fun next()
    fun previous()
    fun seekTo(progress: Float)
    fun toggleShuffle()
    fun cycleRepeatMode()
}

class FakePlayerController : PlayerController {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    override fun setQueue(songs: List<Song>) {
        _queue.value = songs
        if (_state.value.current == null) {
            _state.update { it.copy(current = songs.firstOrNull()) }
        }
    }

    override fun play(song: Song) {
        _state.update { it.copy(current = song, isPlaying = true, progress = 0f) }
    }

    override fun togglePlay() {
        _state.update { it.copy(isPlaying = !it.isPlaying) }
    }

    override fun next() = step(1)

    override fun previous() = step(-1)

    private fun step(delta: Int) {
        val list = _queue.value
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == _state.value.current?.id }
        val target = ((if (index < 0) 0 else index) + delta + list.size) % list.size
        _state.update { it.copy(current = list[target], progress = 0f, isPlaying = true) }
    }

    override fun seekTo(progress: Float) {
        _state.update { it.copy(progress = progress.coerceIn(0f, 1f)) }
    }

    override fun toggleShuffle() {
        _state.update { it.copy(shuffle = !it.shuffle) }
    }

    override fun cycleRepeatMode() {
        _state.update {
            it.copy(
                repeatMode = when (it.repeatMode) {
                    RepeatMode.Off -> RepeatMode.All
                    RepeatMode.All -> RepeatMode.One
                    RepeatMode.One -> RepeatMode.Off
                }
            )
        }
    }
}