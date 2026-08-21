package com.wxjxpp.musicplayer.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.wxjxpp.musicplayer.core.model.MediaLocation
import com.wxjxpp.musicplayer.core.model.PlaybackState
import com.wxjxpp.musicplayer.core.model.RepeatMode
import com.wxjxpp.musicplayer.core.model.ShuffleMode
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
import kotlin.random.Random

/**
 * Media3 播放控制器。
 *
 * 关键设计：
 * - 队列顺序由本类维护（[queue]），ExoPlayer 只负责单曲解码，
 *   这样"真随机 / 伪随机"两种策略可以完全由我们控制，
 *   不受 ExoPlayer 内部 shuffle 实现的限制。
 * - 真随机：每次下一首都重新掷骰子，可能连续重复。
 * - 伪随机：预生成一轮洗牌顺序，一轮播完再重新洗，不会重复。
 */
@OptIn(UnstableApi::class)
class Media3PlayerController(
    context: Context,
    private val scope: CoroutineScope,
    private val progressIntervalMs: Long = 250L,
) : PlayerController {

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    /** 伪随机使用的预洗牌顺序（存的是 queue 下标）。 */
    private var shuffleOrder: List<Int> = emptyList()
    private var shuffleCursor: Int = 0

    private var progressJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startProgressTicker() else stopProgressTicker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(durationMs = player.duration.coerceAtLeast(0L)) }
                }
                if (playbackState == Player.STATE_ENDED) onTrackFinished()
            }
        })
    }

    override fun setQueue(songs: List<Song>, startIndex: Int, autoPlay: Boolean) {
        _queue.value = songs
        rebuildShuffleOrder()
        val target = songs.getOrNull(startIndex) ?: songs.firstOrNull()
        if (target == null) {
            _state.value = PlaybackState()
            return
        }
        _state.update { it.copy(current = target, durationMs = target.durationMs, positionMs = 0L) }
        prepare(target, playWhenReady = autoPlay)
    }

    override fun play(song: Song) {
        if (_queue.value.none { it.id == song.id }) {
            _queue.update { it + song }
            rebuildShuffleOrder()
        }
        _state.update { it.copy(current = song, durationMs = song.durationMs, positionMs = 0L) }
        prepare(song, playWhenReady = true)
    }

    override fun togglePlay() {
        if (_state.value.current == null) return
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun pause() = player.pause()

    override fun resume() {
        if (_state.value.current != null) player.play()
    }

    override fun next() = advance(forward = true, userTriggered = true)

    override fun previous() = advance(forward = false, userTriggered = true)

    override fun seekTo(positionMs: Long) {
        val duration = _state.value.durationMs
        val target = positionMs.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    override fun toggleShuffle() {
        _state.update { it.copy(shuffle = !it.shuffle) }
        rebuildShuffleOrder()
    }

    /** 真随机 / 伪随机切换。 */
    fun setShuffleMode(mode: ShuffleMode) {
        _state.update { it.copy(shuffleMode = mode) }
        rebuildShuffleOrder()
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
        val clamped = speed.coerceIn(0.25f, 4f)
        player.setPlaybackSpeed(clamped)
        _state.update { it.copy(speed = clamped) }
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        player.volume = clamped
        _state.update { it.copy(volume = clamped) }
    }

    override fun addToQueue(songs: List<Song>) {
        _queue.update { current -> current + songs.filter { s -> current.none { it.id == s.id } } }
        rebuildShuffleOrder()
    }

    override fun playNext(songs: List<Song>) {
        val list = _queue.value.toMutableList()
        val index = list.indexOfFirst { it.id == _state.value.current?.id }
        list.addAll(if (index < 0) 0 else index + 1, songs.filter { s -> list.none { it.id == s.id } })
        _queue.value = list
        rebuildShuffleOrder()
    }

    override fun removeFromQueue(songId: String) {
        _queue.update { list -> list.filterNot { it.id == songId } }
        rebuildShuffleOrder()
    }

    override fun clearQueue() {
        player.stop()
        player.clearMediaItems()
        _queue.value = emptyList()
        _state.value = PlaybackState()
        stopProgressTicker()
    }

    override fun release() {
        stopProgressTicker()
        player.release()
    }

    // ---- 内部实现 ----

    private fun prepare(song: Song, playWhenReady: Boolean) {
        val uri = when (val loc = song.location) {
            is MediaLocation.Local -> loc.uri
            is MediaLocation.WebDav -> loc.remotePath
            is MediaLocation.Remote -> return // 在线源需先解析播放地址，交由上层处理
        }
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(song.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artistName)
                    .setAlbumTitle(song.albumTitle)
                    .build()
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    /**
     * 推进到下一首。
     *
     * [userTriggered] 为 false 表示是自然播完，此时单曲循环要原地重播。
     */
    private fun advance(forward: Boolean, userTriggered: Boolean) {
        val list = _queue.value
        if (list.isEmpty()) return

        if (!userTriggered && _state.value.repeatMode == RepeatMode.One) {
            seekTo(0L)
            player.play()
            return
        }

        val currentIndex = list.indexOfFirst { it.id == _state.value.current?.id }.coerceAtLeast(0)
        val targetIndex = when {
            _state.value.shuffle -> nextShuffleIndex(list.size, currentIndex, forward)
            forward -> (currentIndex + 1) % list.size
            else -> (currentIndex - 1 + list.size) % list.size
        }
        val song = list.getOrNull(targetIndex) ?: return
        _state.update { it.copy(current = song, durationMs = song.durationMs, positionMs = 0L) }
        prepare(song, playWhenReady = true)
    }

    private fun nextShuffleIndex(size: Int, currentIndex: Int, forward: Boolean): Int {
        if (size <= 1) return 0
        return when (_state.value.shuffleMode) {
            // 真随机：每次独立掷骰，允许重复
            ShuffleMode.True -> Random.nextInt(size)
            // 伪随机：走预洗牌序列，一轮不重复
            ShuffleMode.Pseudo -> {
                if (shuffleOrder.size != size) rebuildShuffleOrder()
                if (shuffleOrder.isEmpty()) return currentIndex
                shuffleCursor = if (forward) {
                    (shuffleCursor + 1) % shuffleOrder.size
                } else {
                    (shuffleCursor - 1 + shuffleOrder.size) % shuffleOrder.size
                }
                // 一轮走完重新洗牌，避免长期固定顺序
                if (shuffleCursor == 0 && forward) rebuildShuffleOrder()
                shuffleOrder.getOrElse(shuffleCursor) { currentIndex }
            }
        }
    }

    private fun rebuildShuffleOrder() {
        val size = _queue.value.size
        shuffleOrder = if (size == 0) emptyList() else (0 until size).shuffled()
        shuffleCursor = 0
    }

    private fun onTrackFinished() {
        when (_state.value.repeatMode) {
            RepeatMode.One -> {
                seekTo(0L)
                player.play()
            }

            RepeatMode.All -> advance(forward = true, userTriggered = false)

            RepeatMode.Off -> {
                val list = _queue.value
                val isLast = list.lastOrNull()?.id == _state.value.current?.id
                if (isLast && !_state.value.shuffle) {
                    _state.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
                    stopProgressTicker()
                } else {
                    advance(forward = true, userTriggered = false)
                }
            }
        }
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        progressJob = scope.launch {
            while (isActive) {
                _state.update {
                    it.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                    )
                }
                delay(progressIntervalMs)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }
}