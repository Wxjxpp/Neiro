package com.wxjxpp.musicplayer.core.player

import com.wxjxpp.musicplayer.core.model.PlaybackState
import com.wxjxpp.musicplayer.core.model.RepeatMode
import com.wxjxpp.musicplayer.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 内存版播放控制器。
 *
 * 不解码音频，只按真实时间推进进度，用于在没接 Media3 之前
 * 让 UI、动画、进度条、队列逻辑都能跑通并被验证。
 *
 * 替换为 Media3 时删掉这个类即可，其余代码不受影响。
 */
class InMemoryPlayerController(
    private val scope: CoroutineScope,
    private val tickIntervalMs: Long = 200L,
) : PlayerController {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private var ticker: Job? = null

    override fun setQueue(songs: List<Song>, startIndex: Int, autoPlay: Boolean) {
        _queue.value = songs
        val target = songs.getOrNull(startIndex) ?: songs.firstOrNull()
        _state.update {
            it.copy(
                current = target,
                durationMs = target?.durationMs ?: 0L,
                positionMs = 0L,
                isPlaying = autoPlay && target != null,
            )
        }
        if (autoPlay) startTicker() else stopTicker()
    }

    override fun play(song: Song) {
        if (_queue.value.none { it.id == song.id }) {
            _queue.update { it + song }
        }
        _state.update {
            it.copy(
                current = song,
                durationMs = song.durationMs,
                positionMs = 0L,
                isPlaying = true,
            )
        }
        startTicker()
    }

    override fun togglePlay() {
        if (_state.value.current == null) return
        val playing = !_state.value.isPlaying
        _state.update { it.copy(isPlaying = playing) }
        if (playing) startTicker() else stopTicker()
    }

    override fun pause() {
        _state.update { it.copy(isPlaying = false) }
        stopTicker()
    }

    override fun resume() {
        if (_state.value.current == null) return
        _state.update { it.copy(isPlaying = true) }
        startTicker()
    }

    override fun next() = step(1)

    override fun previous() = step(-1)

    private fun step(delta: Int) {
        val list = _queue.value
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == _state.value.current?.id }.coerceAtLeast(0)
        val target = when {
            _state.value.shuffle && list.size > 1 -> list.indices.filter { it != index }.random()
            else -> (index + delta + list.size) % list.size
        }
        val song = list[target]
        _state.update {
            it.copy(
                current = song,
                durationMs = song.durationMs,
                positionMs = 0L,
                isPlaying = true,
            )
        }
        startTicker()
    }

    override fun seekTo(positionMs: Long) {
        val duration = _state.value.durationMs
        _state.update { it.copy(positionMs = positionMs.coerceIn(0L, duration)) }
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

    override fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed.coerceIn(0.25f, 4f)) }
    }

    override fun setVolume(volume: Float) {
        _state.update { it.copy(volume = volume.coerceIn(0f, 1f)) }
    }

    override fun addToQueue(songs: List<Song>) {
        _queue.update { it + songs.filter { s -> it.none { q -> q.id == s.id } } }
    }

    override fun playNext(songs: List<Song>) {
        val list = _queue.value.toMutableList()
        val index = list.indexOfFirst { it.id == _state.value.current?.id }
        list.addAll(if (index < 0) 0 else index + 1, songs)
        _queue.value = list
    }

    override fun removeFromQueue(songId: String) {
        _queue.update { list -> list.filterNot { it.id == songId } }
    }

    override fun clearQueue() {
        _queue.value = emptyList()
        _state.value = PlaybackState()
        stopTicker()
    }

    override fun release() = stopTicker()

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (isActive) {
                delay(tickIntervalMs)
                val snapshot = _state.value
                if (!snapshot.isPlaying) continue
                val advanced = snapshot.positionMs + (tickIntervalMs * snapshot.speed).toLong()
                if (snapshot.durationMs > 0 && advanced >= snapshot.durationMs) {
                    onTrackFinished()
                } else {
                    _state.update { it.copy(positionMs = advanced) }
                }
            }
        }
    }

    private fun onTrackFinished() {
        when (_state.value.repeatMode) {
            RepeatMode.One -> _state.update { it.copy(positionMs = 0L) }
            RepeatMode.All -> next()
            RepeatMode.Off -> {
                val list = _queue.value
                val isLast = list.lastOrNull()?.id == _state.value.current?.id
                if (isLast) {
                    _state.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
                    stopTicker()
                } else {
                    next()
                }
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }
}