package com.wxjxpp.neiro.core.player
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.PlaybackState
import com.wxjxpp.neiro.core.model.RepeatMode
import com.wxjxpp.neiro.core.model.ShuffleMode
import com.wxjxpp.neiro.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * 两个关键约束：
 * 1. ExoPlayer 的所有 API 必须在创建它的线程（这里是主线程）调用，
 *    否则会抛 IllegalStateException 直接崩溃。因此对外方法一律经 [onPlayer] 派发。
 * 2. 队列顺序由本类维护，ExoPlayer 只负责单曲解码，
 *    这样真随机 / 伪随机策略完全可控。
 */
@OptIn(UnstableApi::class)
class Media3PlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val progressIntervalMs: Long = 250L,
) : PlayerController {

    /** 拔出耳机自动暂停（含蓝牙断开）。默认开。 */
    @Volatile
    override var pauseOnHeadphoneDisconnect: Boolean = true
        set(value) {
            field = value
            updateNoisyReceiver()
        }

    /** 其他应用抢占音频焦点时暂停。默认开。 */
    @Volatile
    override var pauseOnAudioFocusLoss: Boolean = true

    /** AUDIO_BECOMING_NOISY 广播接收器：拔出耳机/断开蓝牙时系统会发此广播。 */
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY &&
                pauseOnHeadphoneDisconnect
            ) {
                onPlayer { p -> p.pause() }
            }
        }
    }
    private var noisyReceiverRegistered = false

    /** 按设置开关注册/注销耳机拔出广播。仅在播放中才有必要监听。 */
    private fun updateNoisyReceiver() {
        if (pauseOnHeadphoneDisconnect && !noisyReceiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyReceiverRegistered = true
        } else if (!pauseOnHeadphoneDisconnect && noisyReceiverRegistered) {
            context.unregisterReceiver(becomingNoisyReceiver)
            noisyReceiverRegistered = false
        }
    }

    /**
     * 在线歌曲取流回调。
     *
     * 在线音源的播放地址是临时的，必须播放前解析。这里用回调注入而不是
     * 直接依赖音源注册表，播放层因此不必知道"音源"这个概念。
     * 返回 null 表示取流失败，[onPlaybackError] 会收到原因。
     */
    var remoteUrlResolver: (suspend (Song) -> RemoteUrl)? = null

    /** 播放相关的可展示错误（取流失败、解码失败）。 */
    var onPlaybackError: ((String) -> Unit)? = null

    /** 取流结果。 */
    sealed interface RemoteUrl {
        data class Success(val url: String) : RemoteUrl
        data class Failure(val reason: String) : RemoteUrl
    }

    /** 懒初始化：必须在主线程创建。 */
    private var player: ExoPlayer? = null
    /** 实验室音效：8-bit 量化。 */
    private val eightBitProcessor = EightBitAudioProcessor()
    /** 实验室音效：80 倍速（PCM 帧复制）。 */
    private val turboProcessor = TurboSpeedAudioProcessor(80)

    override fun setEightBitMode(enabled: Boolean) {
        _state.update { it.copy(eightBitMode = enabled) }
        eightBitProcessor.setEnabledMode(enabled)
    }

    override fun setTurboSpeedMode(enabled: Boolean) {
        _state.update { it.copy(turboSpeedMode = enabled) }
        turboProcessor.setEnabledMode(enabled)
    }

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private var shuffleOrder: List<Int> = emptyList()
    private var shuffleCursor: Int = 0
    private var progressJob: Job? = null

    /** 正在进行的取流任务；切歌时取消，避免旧结果覆盖新歌。 */
    private var resolveJob: Job? = null

    /** 在主线程执行播放器操作；已在主线程则直接跑，避免多余调度。 */
    private fun onPlayer(block: (ExoPlayer) -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block(ensurePlayer())
        } else {
            scope.launch(Dispatchers.Main.immediate) { block(ensurePlayer()) }
        }
    }

    private fun ensurePlayer(): ExoPlayer = player ?: createPlayer().also { player = it }

    private fun createPlayer(): ExoPlayer {
        // 注入实验室音效处理器：覆写 buildAudioSink 把自定义 AudioProcessor
        // 插到链首（官方 chain 保留在后）
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                // media3 1.8+ 已移除带 processors 的 4 参重载，
                // 这里用 Builder 重建 sink，自定义处理器插链首
                DefaultAudioSink.Builder(context)
                    .setAudioProcessors(
                        arrayOf<AudioProcessor>(eightBitProcessor, turboProcessor),
                    )
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }
        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(false)
            .build()
            .apply {
            addListener(object : Player.Listener {
                /** 系统音频焦点回调：按用户设置决定是否真的暂停。 */
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS &&
                        !pauseOnAudioFocusLoss && playWhenReady == false
                    ) {
                        // 用户关闭了"他源发声暂停"：焦点丢了也继续放
                        onPlayer { p -> p.play() }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) startProgressTicker() else stopProgressTicker()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> _state.update {
                            it.copy(durationMs = duration.coerceAtLeast(0L), isBuffering = false)
                        }

                        Player.STATE_BUFFERING -> _state.update { it.copy(isBuffering = true) }
                        Player.STATE_ENDED -> {
                            _state.update { it.copy(isBuffering = false) }
                            onTrackFinished()
                        }
                    }
                }

                /** 单曲解码失败不能让整个应用崩，跳下一首并上报原因。 */
                override fun onPlayerError(error: PlaybackException) {
                    _state.update { it.copy(isPlaying = false, isBuffering = false) }
                    onPlaybackError?.invoke("播放失败：${error.errorCodeName}")
                    advance(forward = true, userTriggered = false)
                }
            })
        }
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

    /** 从队列里指定索引开始播放（播放列表面板点击用）。 */
    fun playAt(index: Int) {
        val song = _queue.value.getOrNull(index) ?: return
        play(song)
    }

    override fun togglePlay() {
        if (_state.value.current == null) return
        onPlayer { p -> if (p.isPlaying) p.pause() else p.play() }
    }

    override fun pause() = onPlayer { it.pause() }

    override fun resume() {
        if (_state.value.current == null) return
        onPlayer { it.play() }
    }

    override fun next() = advance(forward = true, userTriggered = true)

    override fun previous() = advance(forward = false, userTriggered = true)

    override fun seekTo(positionMs: Long) {
        val duration = _state.value.durationMs
        val target = positionMs.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        _state.update { it.copy(positionMs = target) }
        onPlayer { it.seekTo(target) }
    }

    override fun toggleShuffle() {
        _state.update { it.copy(shuffle = !it.shuffle) }
        rebuildShuffleOrder()
    }

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
        _state.update { it.copy(speed = clamped) }
        onPlayer { it.setPlaybackSpeed(clamped) }
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = clamped) }
        onPlayer { it.volume = clamped }
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
        _queue.value = emptyList()
        _state.value = PlaybackState()
        stopProgressTicker()
        onPlayer { p ->
            p.stop()
            p.clearMediaItems()
        }
    }

    override fun release() {
        stopProgressTicker()
        if (noisyReceiverRegistered) {
            runCatching { context.unregisterReceiver(becomingNoisyReceiver) }
            noisyReceiverRegistered = false
        }
        onPlayer { it.release() }
    }

    // ---- 内部实现 ----

    private fun prepare(song: Song, playWhenReady: Boolean) {
        resolveJob?.cancel()
        when (val loc = song.location) {
            is MediaLocation.Local -> playUri(song, loc.uri, playWhenReady)
            is MediaLocation.WebDav -> playUri(song, loc.remotePath, playWhenReady)
            // 在线源要先换取临时播放地址
            is MediaLocation.Remote -> resolveAndPlay(song, playWhenReady)
        }
    }

    /** 在线歌曲：异步取流后再交给 ExoPlayer。 */
    private fun resolveAndPlay(song: Song, playWhenReady: Boolean) {
        val resolver = remoteUrlResolver
        if (resolver == null) {
            onPlaybackError?.invoke("未配置在线取流能力")
            return
        }
        // 取消上一次未完成的取流，并立即静默播放器：
        // 否则取流期间旧歌继续出声，取流失败时界面显示 A 却一直在放 B
        resolveJob?.cancel()
        onPlayer { p ->
            p.stop()
            p.clearMediaItems()
        }
        val generation = ++resolveGeneration
        _state.update { it.copy(isBuffering = true) }
        resolveJob = scope.launch {
            val result = runCatching { resolver(song) }.getOrElse { error ->
                RemoteUrl.Failure(error.message ?: "取流失败")
            }
            // 期间用户已经切歌，过期结果直接丢弃
            if (generation != resolveGeneration || _state.value.current?.id != song.id) return@launch
            _state.update { it.copy(isBuffering = false) }
            when (result) {
                is RemoteUrl.Success -> playUri(song, result.url, playWhenReady)
                is RemoteUrl.Failure -> {
                    _state.update { it.copy(isPlaying = false) }
                    onPlaybackError?.invoke("「${song.title}」取流失败：${result.reason}")
                }
            }
        }
    }

    /** 取流代际计数：每次切歌 +1，过期回调据此丢弃。 */
    private var resolveGeneration = 0

    /**
     * 重新解析当前在线歌曲（音质变化后调用）。
     *
     * 保持当前进度：记住位置 → 重新取流 → 恢复播放状态与进度。
     * 本地歌曲或没有当前歌曲时是空操作。
     */
    fun reloadCurrent() {
        val song = _state.value.current ?: return
        if (song.location !is MediaLocation.Remote) return
        val resumePosition = _state.value.positionMs
        val wasPlaying = _state.value.isPlaying
        resolveJob?.cancel()
        onPlayer { p ->
            p.stop()
            p.clearMediaItems()
        }
        val generation = ++resolveGeneration
        _state.update { it.copy(isBuffering = true, positionMs = resumePosition) }
        resolveJob = scope.launch {
            val resolver = remoteUrlResolver
            val result = if (resolver == null) {
                RemoteUrl.Failure("未配置在线取流能力")
            } else {
                runCatching { resolver(song) }.getOrElse { error ->
                    RemoteUrl.Failure(error.message ?: "取流失败")
                }
            }
            if (generation != resolveGeneration || _state.value.current?.id != song.id) return@launch
            _state.update { it.copy(isBuffering = false) }
            when (result) {
                is RemoteUrl.Success -> playUri(song, result.url, wasPlaying, startAtMs = resumePosition)
                is RemoteUrl.Failure -> {
                    _state.update { it.copy(isPlaying = false) }
                    onPlaybackError?.invoke("「${song.title}」取流失败：${result.reason}")
                }
            }
        }
    }

    private fun playUri(song: Song, uri: String, playWhenReady: Boolean, startAtMs: Long = 0L) {
        val item = MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaId(song.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artistName)
                    .setAlbumTitle(song.albumTitle)
                    .apply { song.coverUri?.let { setArtworkUri(Uri.parse(it)) } }
                    .build()
            )
            .build()
        onPlayer { p ->
            p.setMediaItem(item, startAtMs)
            p.prepare()
            p.playWhenReady = playWhenReady
        }
    }

    private fun advance(forward: Boolean, userTriggered: Boolean) {
        val list = _queue.value
        if (list.isEmpty()) return

        if (!userTriggered && _state.value.repeatMode == RepeatMode.One) {
            seekTo(0L)
            onPlayer { it.play() }
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
                onPlayer { it.play() }
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
        progressJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                val p = player
                if (p != null) {
                    _state.update {
                        it.copy(
                            positionMs = p.currentPosition.coerceAtLeast(0L),
                            durationMs = p.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                        )
                    }
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