package com.wxjxpp.neiro.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wxjxpp.neiro.core.data.QualityFallbackDirection
import com.wxjxpp.neiro.core.data.RoomSongRepository
import com.wxjxpp.neiro.core.model.HeatmapDay
import com.wxjxpp.neiro.core.model.Lyrics
import com.wxjxpp.neiro.core.model.PlayEvent
import com.wxjxpp.neiro.core.model.PlaybackState
import com.wxjxpp.neiro.core.model.Playlist
import com.wxjxpp.neiro.core.model.Quality
import com.wxjxpp.neiro.core.model.ShuffleMode
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.model.SongSortField
import com.wxjxpp.neiro.core.search.searchSongs
import com.wxjxpp.neiro.core.userapi.UserApiInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 壳层状态。
 *
 * 页面级复杂状态（如歌单详情）后续可以各自建 ViewModel，
 * 这里只保留全局需要的：曲库、刷新、播放栏样式、多选、搜索、歌词。
 */
data class ShellUiState(
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isRefreshing: Boolean = false,
    val floatingPlayerBar: Boolean = true,
    val downloadDirUri: String = "",
    val downloadEmbedCover: Boolean = true,
    val downloadEmbedLyrics: Boolean = true,
    val shuffleMode: ShuffleMode = ShuffleMode.Pseudo,
    val showTranslation: Boolean = true,

    /** 长按多选：非空即处于多选态。 */
    val selectedSongIds: Set<String> = emptySet(),

    val searchQuery: String = "",
    val searchResults: List<Song> = emptyList(),

    /** 在线搜索。 */
    val onlineSearchPlatform: String = "all",
    val onlineResults: List<Song> = emptyList(),
    val onlineFailedPlatforms: List<String> = emptyList(),
    val isSearchingOnline: Boolean = false,
    val onlinePlatforms: List<com.wxjxpp.neiro.core.search.OnlineSearchRepository.PlatformOption> = emptyList(),

    /** 当前歌曲的歌词。 */
    val lyrics: Lyrics = Lyrics.Empty,

    val userApis: List<UserApiInfo> = emptyList(),

    /** 音源引擎状态。 */
    val userApiStatus: com.wxjxpp.neiro.core.userapi.UserApiStatus? = null,

    /** 网易云 Cookie（设置页编辑）。 */
    val neteaseCookie: String = "",

    /** 歌曲列表排序。 */
    val songSortField: SongSortField = SongSortField.Title,
    val songSortDescending: Boolean = false,
    /** 专辑页排序（内存态，独立于歌曲列表的持久化设置）。 */
    val albumSortField: com.wxjxpp.neiro.feature.albums.AlbumSortField =
        com.wxjxpp.neiro.feature.albums.AlbumSortField.Title,
    val albumSortDescending: Boolean = false,

    /** 听歌热力图（最近一年）。 */
    val heatmapDays: List<HeatmapDay> = emptyList(),
    val isHeatmapLoading: Boolean = false,

    /** 歌词手动偏移（毫秒），正数 = 歌词提前。 */
    val lyricsOffsetMs: Long = 0L,

    /** 拔出耳机自动暂停。 */
    val pauseOnHeadphoneDisconnect: Boolean = true,
    /** 他源发声自动暂停。 */
    val pauseOnAudioFocusLoss: Boolean = true,
    /** 播放页动态流光背景。 */
    val ambientGlow: Boolean = false,

    /** 歌词对齐：start / center / end。 */
    val lyricsAlign: String = "center",

    /** [实验室] 歌词弹簧动效。 */
    val labSpringLyrics: Boolean = false,

    /** 歌词字号缩放。 */
    val lyricsFontScale: Float = 1f,
    /** 歌词行间隙缩放。 */
    val lyricsGapScale: Float = 1f,
    /** 纯净模式默认开启。 */
    val pureModeDefault: Boolean = false,
    /** [实验室] 8-bit 播放模式。 */
    val lab8Bit: Boolean = false,
    /** [实验室] 80 倍速播放模式。 */
    val labTurboSpeed: Boolean = false,
    /** 启动时恢复上次播放。 */
    val resumeOnStart: Boolean = false,
    /** 启动时自动继续播放。 */
    val autoPlayOnStart: Boolean = false,
    /** 在线播放偏好音质（播放页可临时改）。 */
    val preferredQuality: Quality = Quality.Standard,
    /** 取流失败时的音质回退方向。 */
    val qualityFallbackDirection: QualityFallbackDirection = QualityFallbackDirection.LOWER,
    /** 全局字号缩放（0.8~1.4）。 */
    val appFontScale: Float = 1f,
    /** 字体样式：default / serif / mono / cursive。 */
    val appFontFamily: String = "default",

    /** 发现页区块数据。 */
    val discoverSections: List<com.wxjxpp.neiro.core.discover.DiscoverRepository.Section> = emptyList(),
    /** 发现页二级详情：当前展开的榜单 id 与完整曲目。 */
    val discoverDetailId: String? = null,
    val discoverDetailSongs: List<Song> = emptyList(),
    val isDiscoverDetailLoading: Boolean = false,
    /** 本地收藏夹（在线/本地歌曲均可），新收藏在前。 */
    val favoriteSongs: List<Song> = emptyList(),
    /** 批量下载进行中的歌曲 id（用于按钮转圈/防重复点击）。 */
    val downloadingIds: Set<String> = emptySet(),
    val isDiscoverLoading: Boolean = false,
    /** 最近播放（快照反序列化，新歌在前）。 */
    val recentSongs: List<Song> = emptyList(),
    /** 全局错误提示：顶部横幅展示，可关闭 / 上滑关闭。 */
    val errorMessage: String? = null,
)

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = container.playerController.state
    val queue: StateFlow<List<Song>> = container.playerController.queue
    /** 播放页 Sheet 进度（0 收起 / 1 播放页 / 2 歌词页），拖拽跟手。 */
    val sheetProgress: StateFlow<Float> = container.playerController.sheetProgress

    /** 拖拽过程中实时写入 Sheet 进度（跟手，无动画）。 */
    fun setSheetProgress(value: Float) {
        container.playerController.setSheetProgress(value)
    }

    /** 记录当前歌曲的开始时间，用于生成播放事件。 */
    private var currentSongStartedAt: Long = 0L
    private var currentSongId: String? = null
    /** 歌曲 id → 播放次数（排序用）。 */
    private var playCounts: Map<String, Int> = emptyMap()

    init {
        // 搜索页音源筛选条：全部 + 各平台（含已导入 LX 脚本派生的音源）
        _uiState.update { it.copy(onlinePlatforms = container.onlineSearch.platforms) }
        container.songRepository.observeSongs()
            .onEach { songs ->
                _uiState.update { it.copy(songs = sortSongs(songs)) }
                if (queue.value.isEmpty() && songs.isNotEmpty()) {
                    container.playerController.setQueue(songs, autoPlay = false)
                }
                // 曲库变化后刷新搜索结果
                refreshSearch()
            }
            .launchIn(viewModelScope)

        container.playlistRepository.observePlaylists()
            .onEach { list -> _uiState.update { it.copy(playlists = list) } }
            .launchIn(viewModelScope)

        container.settingsRepository.observeFloatingPlayerBar()
            .onEach { enabled -> _uiState.update { it.copy(floatingPlayerBar = enabled) } }
            .launchIn(viewModelScope)
        container.appSettings.observeDownloadDirUri()
            .onEach { uri -> _uiState.update { it.copy(downloadDirUri = uri) } }
            .launchIn(viewModelScope)
        container.appSettings.downloadEmbedCover
            .onEach { v -> _uiState.update { it.copy(downloadEmbedCover = v) } }
            .launchIn(viewModelScope)
        container.appSettings.downloadEmbedLyrics
            .onEach { v -> _uiState.update { it.copy(downloadEmbedLyrics = v) } }
            .launchIn(viewModelScope)

        container.settingsRepository.observeShowTranslation()
            .onEach { enabled -> _uiState.update { it.copy(showTranslation = enabled) } }
            .launchIn(viewModelScope)

        container.appSettings.observeShuffleMode()
            .onEach { mode -> _uiState.update { it.copy(shuffleMode = mode) } }
            .launchIn(viewModelScope)

        container.userApiStore.apis
            .onEach { list -> _uiState.update { state -> state.copy(userApis = list) } }
            .launchIn(viewModelScope)

        container.userApiEngine.status
            .onEach { status ->
                _uiState.update {
                    it.copy(
                        userApiStatus = status,
                        // 外置源集合随脚本状态变化，筛选条同步刷新
                        onlinePlatforms = container.onlineSearch.platforms,
                    )
                }
            }
            .launchIn(viewModelScope)

        // 网易云 Cookie
        container.appSettings.observeNeteaseCookie()
            .onEach { cookie -> _uiState.update { it.copy(neteaseCookie = cookie) } }
            .launchIn(viewModelScope)
        // 歌曲排序设置
        container.appSettings.observeSongSortField()
            .onEach { field -> _uiState.update { it.copy(songSortField = field) } }
            .launchIn(viewModelScope)
        container.appSettings.observeSongSortDescending()
            .onEach { desc -> _uiState.update { it.copy(songSortDescending = desc) } }
            .launchIn(viewModelScope)
        // 播放次数统计（排序用）：每次播放事件变化后重新聚合
        container.statsRepository.observeRecent(limit = 2000)
            .map { events -> events.groupingBy { it.songId }.eachCount() }
            .onEach { counts ->
                playCounts = counts
                applySongSort()
            }
            .launchIn(viewModelScope)
        // 歌词偏移
        container.appSettings.observeLyricsOffset()
            .onEach { offset -> _uiState.update { it.copy(lyricsOffsetMs = offset) } }
            .launchIn(viewModelScope)
        // 耳机/焦点/流光设置，并同步到播放器
        container.appSettings.observePauseOnHeadphoneDisconnect()
            .onEach { enabled ->
                _uiState.update { it.copy(pauseOnHeadphoneDisconnect = enabled) }
                container.playerController.pauseOnHeadphoneDisconnect = enabled
            }
            .launchIn(viewModelScope)
        container.appSettings.observePauseOnAudioFocusLoss()
            .onEach { enabled ->
                _uiState.update { it.copy(pauseOnAudioFocusLoss = enabled) }
                container.playerController.pauseOnAudioFocusLoss = enabled
            }
            .launchIn(viewModelScope)
        container.appSettings.observeAmbientGlow()
            .onEach { enabled -> _uiState.update { it.copy(ambientGlow = enabled) } }
            .launchIn(viewModelScope)
        container.appSettings.observeLyricsAlign()
            .onEach { align -> _uiState.update { it.copy(lyricsAlign = align) } }
            .launchIn(viewModelScope)
        container.appSettings.observeLabSpringLyrics()
            .onEach { enabled -> _uiState.update { it.copy(labSpringLyrics = enabled) } }
            .launchIn(viewModelScope)
        container.appSettings.observeLyricsFontScale()
            .onEach { scale -> _uiState.update { it.copy(lyricsFontScale = scale) } }
            .launchIn(viewModelScope)
        container.appSettings.observeLyricsGapScale()
            .onEach { scale -> _uiState.update { it.copy(lyricsGapScale = scale) } }
            .launchIn(viewModelScope)
        container.appSettings.observePureModeDefault()
            .onEach { enabled -> _uiState.update { it.copy(pureModeDefault = enabled) } }
            .launchIn(viewModelScope)
        container.appSettings.observeLab8Bit()
            .onEach { enabled ->
                _uiState.update { it.copy(lab8Bit = enabled) }
                container.playerController.setEightBitMode(enabled)
            }
            .launchIn(viewModelScope)
        container.appSettings.observeLabTurboSpeed()
            .onEach { enabled ->
                _uiState.update { it.copy(labTurboSpeed = enabled) }
                container.playerController.setTurboSpeedMode(enabled)
            }
            .launchIn(viewModelScope)
        container.appSettings.observeResumeOnStart()
            .onEach { enabled -> _uiState.update { it.copy(resumeOnStart = enabled) } }
            .launchIn(viewModelScope)
        container.appSettings.observeAutoPlayOnStart()
            .onEach { enabled -> _uiState.update { it.copy(autoPlayOnStart = enabled) } }
            .launchIn(viewModelScope)
        // 在线音质与回退方向
        container.appSettings.observePreferredQuality()
            .onEach { quality -> _uiState.update { it.copy(preferredQuality = quality) } }
            .launchIn(viewModelScope)
        container.appSettings.observeQualityFallbackDirection()
            .onEach { direction -> _uiState.update { it.copy(qualityFallbackDirection = direction) } }
            .launchIn(viewModelScope)
        // 全局字体设置
        container.appSettings.observeAppFontScale()
            .onEach { scale -> _uiState.update { it.copy(appFontScale = scale) } }
            .launchIn(viewModelScope)
        container.appSettings.observeAppFontFamily()
            .onEach { family -> _uiState.update { it.copy(appFontFamily = family) } }
            .launchIn(viewModelScope)
        // 顶部错误横幅（取流失败等）
        container.errorBanner
            .onEach { message -> _uiState.update { it.copy(errorMessage = message) } }
            .launchIn(viewModelScope)
        // 播放进度记忆：每 5 秒采样落盘一次 + 切歌立即记录（含歌曲快照，跨会话可恢复）
        container.playerController.state
            .sample(5_000L)
            .onEach { state ->
                val id = state.current?.id
                if (id != null && state.positionMs > 0L) {
                    container.appSettings.savePlaybackProgress(
                        id,
                        state.positionMs,
                        com.wxjxpp.neiro.core.serialization.SongJson.toJson(state.current!!),
                    )
                    lastSavedSongId = id
                }
            }
            .launchIn(viewModelScope)
        restoreLastPlayback()
        // 切歌时：结算上一首收听时长 → 写最近播放快照 → 加载新歌词
        playbackState
            .onEach { state -> onSongChanged(state.current) }
            .launchIn(viewModelScope)
        // 最近播放与收藏夹：启动即加载
        launchRecentLoad()
        loadFavorites()
    }

    // ---- 曲库 ----

    /** 本次进程是否已做过首次扫描（防止空列表时反复触发）。 */
    var hasScannedOnce = false

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            runCatching { container.songRepository.rescanLocal() }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // ---- 设置 ----

    /** 按当前排序设置对歌曲列表排序。 */
    private fun sortSongs(songs: List<Song>): List<Song> {
        val state = _uiState.value
        val sorted = when (state.songSortField) {
            SongSortField.Title -> songs.sortedWith(compareBy(java.text.Collator.getInstance()) { it.title })
            SongSortField.AddedTime -> songs.sortedBy { it.addedAt }
            SongSortField.PlayCount -> songs.sortedByDescending { playCounts[it.id] ?: 0 }
            // 专辑排序：专辑名 → 曲目号 → 标题，同专辑歌曲自然聚在一起
            SongSortField.Album -> {
                val collator = java.text.Collator.getInstance()
                songs.sortedWith { a, b ->
                    val byAlbum = collator.compare(a.albumTitle, b.albumTitle)
                    if (byAlbum != 0) byAlbum
                    else {
                        val byTrack = (a.trackNumber ?: Int.MAX_VALUE).compareTo(b.trackNumber ?: Int.MAX_VALUE)
                        if (byTrack != 0) byTrack else collator.compare(a.title, b.title)
                    }
                }
            }
        }
        return if (state.songSortDescending) sorted.asReversed() else sorted
    }

    /** 排序设置变化后重排当前列表。 */
    private fun applySongSort() {
        _uiState.update { it.copy(songs = sortSongs(it.songs)) }
    }

    fun setSongSortField(field: SongSortField) {
        viewModelScope.launch { container.appSettings.setSongSortField(field) }
    }

    fun setSongSortDescending(descending: Boolean) {
        viewModelScope.launch { container.appSettings.setSongSortDescending(descending) }
    }
    /** 专辑页排序（仅内存态，不写设置、不影响歌曲页）。 */
    fun setAlbumSortField(field: com.wxjxpp.neiro.feature.albums.AlbumSortField) {
        _uiState.update { it.copy(albumSortField = field) }
    }
    fun setAlbumSortDescending(descending: Boolean) {
        _uiState.update { it.copy(albumSortDescending = descending) }
    }

    /** 加载听歌热力图（近两年，覆盖月视图向前翻页）。 */
    fun loadHeatmap() {
        if (_uiState.value.isHeatmapLoading) return
        _uiState.update { it.copy(isHeatmapLoading = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val twoYearsAgo = now - 730L * 24 * 60 * 60 * 1000
            val days = runCatching { container.statsRepository.heatmap(twoYearsAgo, now) }
                .getOrDefault(emptyList<HeatmapDay>())
            _uiState.update { it.copy(heatmapDays = days, isHeatmapLoading = false) }
        }
    }

    fun setFloatingPlayerBar(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setFloatingPlayerBar(enabled) }
    }
    /** 下载目录（SAF tree URI）；空串恢复默认公共目录 */
    fun setDownloadDir(uri: String?) {
        viewModelScope.launch {
            container.appSettings.setDownloadDirUri(uri.orEmpty())
        }
    }
    fun setDownloadEmbedCover(v: Boolean) =
        viewModelScope.launch { container.appSettings.setDownloadEmbedCover(v) }
    fun setDownloadEmbedLyrics(v: Boolean) =
        viewModelScope.launch { container.appSettings.setDownloadEmbedLyrics(v) }

    fun setShuffleMode(mode: ShuffleMode) {
        viewModelScope.launch { container.appSettings.setShuffleMode(mode) }
    }

    fun setShowTranslation(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setShowTranslation(enabled) }
    }

    // ---- 播放 ----

    fun play(song: Song) = container.playerController.play(song)

    /** 下载在线歌曲文件到公共音乐目录。开始/结束都走顶部横幅，进度可见。 */
    fun downloadSong(song: Song) {
        container.showError("开始下载「${song.title}」，请在通知栏查看进度")
        viewModelScope.launch { container.showError(container.downloadManager.downloadSong(song)) }
    }
    /** 下载在线歌词到公共文档目录。 */
    fun downloadLyrics(song: Song) {
        viewModelScope.launch { container.showError(container.downloadManager.downloadLyrics(song)) }
    }
    fun togglePlay() = container.playerController.togglePlay()
    fun next() = container.playerController.next()
    fun previous() = container.playerController.previous()
    fun toggleShuffle() = container.playerController.toggleShuffle()
    fun cycleRepeat() = container.playerController.cycleRepeatMode()

    fun seekToFraction(fraction: Float) {
        val duration = playbackState.value.durationMs
        container.playerController.seekTo((duration * fraction).toLong())
    }

    /** 点击歌词行跳转到指定时间。 */
    fun seekTo(positionMs: Long) = container.playerController.seekTo(positionMs)

    /** 倍速播放（0.5x ~ 3.0x）。 */
    fun setSpeed(speed: Float) = container.playerController.setSpeed(speed)

    /**
     * 本地歌曲手动从网络匹配歌词。
     * 直接走 LyricsLocator 的在线兜底（按"歌名 + 歌手"匹配），命中后写入缓存。
     */
    fun matchLyricsOnline() {
        val song = playbackState.value.current ?: return
        viewModelScope.launch {
            val lyrics = runCatching { container.lyricsRepository.lyricsFor(song) }
                .getOrDefault(Lyrics.Empty)
            if (lyrics.isEmpty) {
                container.notify("未能在网络中匹配到歌词")
            } else {
                _uiState.update { it.copy(lyrics = lyrics) }
            }
        }
    }

    fun setLyricsOffset(offsetMs: Long) {
        viewModelScope.launch { container.appSettings.setLyricsOffset(offsetMs) }
    }

    fun setPauseOnHeadphoneDisconnect(enabled: Boolean) {
        viewModelScope.launch { container.appSettings.setPauseOnHeadphoneDisconnect(enabled) }
    }

    fun setPauseOnAudioFocusLoss(enabled: Boolean) {
        viewModelScope.launch { container.appSettings.setPauseOnAudioFocusLoss(enabled) }
    }

    fun setAmbientGlow(enabled: Boolean) {
        viewModelScope.launch { container.appSettings.setAmbientGlow(enabled) }
    }

    fun setLyricsAlign(align: String) {
        viewModelScope.launch { container.appSettings.setLyricsAlign(align) }
    }

    fun setLabSpringLyrics(enabled: Boolean) {
        viewModelScope.launch { container.appSettings.setLabSpringLyrics(enabled) }
    }

    fun setLyricsFontScale(scale: Float) {
        viewModelScope.launch { container.appSettings.setLyricsFontScale(scale) }
    }

        fun setLyricsGapScale(scale: Float) {
        viewModelScope.launch { container.appSettings.setLyricsGapScale(scale) }
    }

    fun setPureModeDefault(enabled: Boolean) {
        viewModelScope.launch { container.appSettings.setPureModeDefault(enabled) }
    }

    /** [实验室] 8-bit 播放模式。 */
    fun setLab8Bit(enabled: Boolean) {
        viewModelScope.launch { container.appSettings.setLab8Bit(enabled) }
    }

    /** [实验室] 80 倍速播放模式。 */
    fun setLabTurboSpeed(enabled: Boolean) {
        viewModelScope.launch { container.appSettings.setLabTurboSpeed(enabled) }
    }

    /** 启动时恢复上次播放。 */
    fun setResumeOnStart(enabled: Boolean) {
        viewModelScope.launch { container.appSettings.setResumeOnStart(enabled) }
    }

    /** 启动时自动继续播放。 */
    fun setAutoPlayOnStart(enabled: Boolean) {
        viewModelScope.launch { container.appSettings.setAutoPlayOnStart(enabled) }
    }

    /** 在线播放偏好音质（持久化；正在播放的在线歌曲会立即重新取流）。 */
    fun setPreferredQuality(quality: Quality) {
        viewModelScope.launch {
            val old = _uiState.value.preferredQuality
            container.appSettings.setPreferredQuality(quality)
            if (old == quality) return@launch
            // 正在播在线歌曲：按新音质重新取流（保持进度），否则下一首歌才生效
            val current = playbackState.value.current
            if (current?.location is com.wxjxpp.neiro.core.model.MediaLocation.Remote) {
                (container.playerController as? com.wxjxpp.neiro.core.player.Media3PlayerController)
                    ?.reloadCurrent()
                container.notify("音质已切换，正在按新档位重新取流…")
            }
        }
    }

    /** 取流失败时的音质回退方向。 */
    fun setQualityFallbackDirection(direction: QualityFallbackDirection) {
        viewModelScope.launch { container.appSettings.setQualityFallbackDirection(direction) }
    }

        /** 全局字号缩放（0.8~1.4），实时生效。 */
    fun setAppFontScale(scale: Float) {
        viewModelScope.launch { container.appSettings.setAppFontScale(scale) }
    }

    /** 字体样式切换（default/serif/mono/cursive），实时生效。 */
    fun setAppFontFamily(id: String) {
        viewModelScope.launch { container.appSettings.setAppFontFamily(id) }
    }

    // ---- 发现页 ----

    /** 拉取发现页数据：各榜单预览。 */
    fun loadDiscover() {
        if (_uiState.value.isDiscoverLoading) return
        _uiState.update { it.copy(isDiscoverLoading = true) }
        viewModelScope.launch {
            val sections = runCatching {
                container.discoverRepository.homeSections(songsPerSection = 20)
            }.getOrDefault(emptyList())
            _uiState.update { it.copy(discoverSections = sections, isDiscoverLoading = false) }
        }
    }

    /** 发现页二级菜单：拉取单个榜单的最近 50 首。 */
    fun loadDiscoverDetail(listId: String) {
        _uiState.update { it.copy(discoverDetailId = listId, isDiscoverDetailLoading = true) }
        viewModelScope.launch {
            val songs = container.discoverRepository.discoverSongs(listId, limit = 50)
            _uiState.update { it.copy(discoverDetailSongs = songs, isDiscoverDetailLoading = false) }
        }
    }

    fun closeDiscoverDetail() {
        _uiState.update { it.copy(discoverDetailId = null, discoverDetailSongs = emptyList()) }
    }

    /** 榜单全部播放：整榜入队，从第一首开始。播放一律走用户导入的自定义音源。 */
    fun playDiscoverList(songs: List<Song>) {
        if (songs.isEmpty()) return
        container.playerController.setQueue(songs, autoPlay = true)
    }

    /** 拉取最近播放快照（进入发现页时刷新）。 */
    fun loadRecentSongs() {
        viewModelScope.launch {
            val json = container.appSettings.observeRecentSongsJson().first()
            val arr = runCatching { org.json.JSONArray(json) }.getOrDefault(org.json.JSONArray())
            val songs = (0 until arr.length()).mapNotNull { i ->
                com.wxjxpp.neiro.core.serialization.SongJson.fromJson(
                    arr.optJSONObject(i)?.toString() ?: return@mapNotNull null,
                )
            }
            _uiState.update { it.copy(recentSongs = songs) }
        }
    }

    /** 启动时加载最近播放（供猜你喜欢做推荐种子）。 */
    fun launchRecentLoad() = loadRecentSongs()

    // ---- 本地收藏夹 ----
    private fun parseSnapshotArray(json: String): List<Song> {
        val arr = runCatching { org.json.JSONArray(json) }.getOrDefault(org.json.JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            com.wxjxpp.neiro.core.serialization.SongJson.fromJson(
                arr.optJSONObject(i)?.toString() ?: return@mapNotNull null,
            )
        }
    }
    /** 收藏夹：内存态列表 + DataStore 快照双写。 */
    private suspend fun persistFavorites(songs: List<Song>) {
        val arr = org.json.JSONArray()
        songs.forEach { arr.put(org.json.JSONObject(com.wxjxpp.neiro.core.serialization.SongJson.toJson(it))) }
        container.appSettings.saveFavoriteSongsJson(arr.toString())
    }
    /** 轻提示转发到容器（Snackbar 展示）。 */
    private fun notify(message: String) = container.notify(message)
    fun loadFavorites() {
        viewModelScope.launch {
            val songs = parseSnapshotArray(container.appSettings.observeFavoriteSongsJson().first())
            _uiState.update { it.copy(favoriteSongs = songs) }
        }
    }
    /** 是否已收藏（按歌曲 id 判定）。 */
    fun isFavorite(songId: String): Boolean =
        _uiState.value.favoriteSongs.any { it.id == songId }
    /** 收藏/取消收藏单曲。 */
    fun toggleFavorite(song: Song) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val current = parseSnapshotArray(container.appSettings.observeFavoriteSongsJson().first())
            val updated = if (current.any { it.id == song.id }) {
                current.filterNot { it.id == song.id }
            } else {
                listOf(song) + current
            }
            persistFavorites(updated)
            _uiState.update { it.copy(favoriteSongs = updated) }
        }
    }
    /** 连续收藏多首（已存在的跳过）。 */
    fun addFavorites(songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val current = parseSnapshotArray(container.appSettings.observeFavoriteSongsJson().first())
            val existingIds = current.mapTo(mutableSetOf()) { it.id }
            val merged = songs.filter { it.id !in existingIds } + current
            persistFavorites(merged)
            _uiState.update { it.copy(favoriteSongs = merged) }
        }
    }

    // ---- 批量下载 ----
    /** 连续下载多首（逐首排队，失败不中断；完成一首移除一个进行中标记）。 */
    fun downloadSongs(songsToDownload: List<Song>) {
        if (songsToDownload.isEmpty()) return
        container.showError(
            "开始下载 ${songsToDownload.size} 首，请在通知栏查看进度",
        )
        viewModelScope.launch {
            _uiState.update {
                it.copy(downloadingIds = it.downloadingIds + songsToDownload.map { s -> s.id }.toSet())
            }
            var ok = 0
            for (s in songsToDownload) {
                try {
                    container.downloadManager.downloadSong(s)
                    ok++
                } catch (e: Exception) {
                    container.showError("下载失败：${s.title}（${e.message?.take(60)}）")
                }
                _uiState.update { it.copy(downloadingIds = it.downloadingIds - s.id) }
            }
            if (songsToDownload.size > 1) {
                container.showError("批量下载完成 $ok/${songsToDownload.size}，请到 Music/Neiro 查看")
            }
        }
    }

    // ---- 错误横幅 ----

    /** 关闭顶部错误横幅。 */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** 歌曲页"随机一发"：优先在线歌曲，无在线结果时回退本地曲库。 */
    fun playRandom() {
        val online = _uiState.value.onlineResults
        if (online.isNotEmpty()) {
            container.playerController.play(online.random())
        } else {
            val songs = _uiState.value.songs
            if (songs.isEmpty()) return
            container.playerController.play(songs.random())
        }
    }

    private var lastSavedSongId: String? = null
    private var restoreAttempted = false

    /**
     * 恢复上次播放进度（应用启动时调用一次）。
     *
     * 优先用歌曲快照（在线歌曲不在本地曲库，只能靠快照恢复），
     * 快照不存在或损坏时退回旧的 songId 查找逻辑。
     */
    private fun restoreLastPlayback() {
        if (restoreAttempted) return
        restoreAttempted = true
        viewModelScope.launch {
            val settings = container.appSettings
            val resumeEnabled = settings.observeResumeOnStart().first()
            val autoPlay = settings.observeAutoPlayOnStart().first()
            // 两个开关都关着就不恢复
            if (!resumeEnabled && !autoPlay) return@launch
            val positionMs = settings.observeLastPositionMs().first()
            // 1) 快照优先：能恢复任何来源的歌（含搜索后点播的在线歌曲）
            val song = settings.observeLastSongJson().first()
                ?.let { com.wxjxpp.neiro.core.serialization.SongJson.fromJson(it) }
                ?: run {
                    // 2) 旧逻辑兜底：按 songId 在本地曲库里找
                    val songId = settings.observeLastSongId().first() ?: return@launch
                    container.songRepository.observeSongs()
                        .first { it.isNotEmpty() }
                        .find { it.id == songId }
                }
                ?: return@launch
            container.playerController.setQueue(listOf(song), autoPlay = false)
            if (positionMs > 0L) container.playerController.seekTo(positionMs)
            if (autoPlay) container.playerController.resume()
        }
    }

    /** 播放整个选中的歌曲集合。 */
    fun playSelected() {
        val selected = selectedSongs()
        if (selected.isEmpty()) return
        container.playerController.setQueue(selected, autoPlay = true)
        clearSelection()
    }

    // ---- 多选 ----

    fun toggleSelection(songId: String) {
        _uiState.update { state ->
            val next = state.selectedSongIds.toMutableSet()
            if (!next.add(songId)) next.remove(songId)
            state.copy(selectedSongIds = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedSongIds = emptySet()) }
    }

    fun selectAll() {
        _uiState.update { state -> state.copy(selectedSongIds = state.songs.map { it.id }.toSet()) }
    }

    /** 从曲库移除选中歌曲（不删磁盘文件）。 */
    fun deleteSelected() {
        val ids = _uiState.value.selectedSongIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            (container.songRepository as? RoomSongRepository)?.delete(ids)
            clearSelection()
        }
    }

    /** 单曲移除出曲库（不删除磁盘文件）。 */
    fun removeSongFromLibrary(songId: String) {
        viewModelScope.launch {
            (container.songRepository as? RoomSongRepository)?.delete(listOf(songId))
            container.notify("已从曲库移除")
        }
    }

    /**
     * 发起系统级文件删除请求。
     *
     * Android 11+ 走 MediaStore createDeleteRequest（弹系统确认框）；
     * Android 10 及以下直接按路径删除。[onNeedSystemConfirm] 拿到 IntentSender
     * 后由 UI 层启动确认页，用户同意后回调 [onFinalize] 收尾。
     */
    fun requestDeleteFile(
        song: Song,
        onNeedSystemConfirm: (android.content.IntentSender) -> Unit,
        onFinalize: () -> Unit,
    ) {
        val local = song.location as? com.wxjxpp.neiro.core.model.MediaLocation.Local
        val mediaId = song.id.removePrefix("media:").toLongOrNull()
        if (local == null || mediaId == null) {
            container.notify("无法定位文件，仅支持本地扫描的歌曲")
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val pending = runCatching {
                android.provider.MediaStore.createDeleteRequest(
                    container.appContext.contentResolver,
                    listOf(android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId,
                    )),
                )
            }.getOrNull()
            if (pending != null) {
                onNeedSystemConfirm(pending.intentSender)
                return
            }
            container.notify("系统拒绝了删除请求")
            return
        }
        // Android 10 及以下：有存储权限时可直接删
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val file = java.io.File(local.filePath ?: "")
            val ok = file.exists() && file.delete()
            if (ok) {
                removeSongFromLibrary(song.id)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onFinalize() }
                container.notify("文件已删除")
            } else {
                container.notify("文件删除失败")
            }
        }
    }

    /** 文件已由系统删除后调用：清理曲库记录并提示。 */
    fun finalizeFileDeleted(songId: String) {
        removeSongFromLibrary(songId)
    }

    fun addSelectedToPlaylist(playlistId: String) {
        val ids = _uiState.value.selectedSongIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            container.playlistRepository.addSongs(playlistId, ids)
            clearSelection()
        }
    }
    /** 批量：把若干歌曲加入已有歌单。 */
    fun addSongsToPlaylist(playlistId: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 在线/收藏歌曲不在本地曲库表里：先落库（upsert 幂等），否则歌单详情查不到
            val known = container.songRepository.observeSongs().first()
                .mapTo(mutableSetOf()) { it.id }
            val missing = songs.filter { it.id !in known }
            if (missing.isNotEmpty()) container.songRepository.upsert(missing)
            container.playlistRepository.addSongs(playlistId, songs.map { it.id })
            notify("已加入歌单（${songs.size} 首）")
        }
    }
    /** 批量：新建歌单并加入歌曲。 */
    fun createPlaylistWithSongs(name: String, songs: List<Song>) {
        if (songs.isEmpty() || name.isBlank()) return
viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 同 addSongsToPlaylist：在线歌曲先落库，避免歌单详情为空
            val known = container.songRepository.observeSongs().first()
                .mapTo(mutableSetOf()) { it.id }
            val missing = songs.filter { it.id !in known }
            if (missing.isNotEmpty()) container.songRepository.upsert(missing)
            container.playlistRepository.create(name.trim(), songs.map { it.id })
            notify("已创建「${name.trim()}」并加入 ${songs.size} 首")
        }
    }

    /** 新建歌单并把选中歌曲放进去。 */
    fun createPlaylistWithSelected(name: String) {
        val ids = _uiState.value.selectedSongIds.toList()
        viewModelScope.launch {
            container.playlistRepository.create(name, ids)
            clearSelection()
        }
    }

    // ---- 歌单 ----

    fun createPlaylist(name: String) {
        viewModelScope.launch { container.playlistRepository.create(name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { container.playlistRepository.delete(id) }
    }

    fun renamePlaylist(id: String, name: String) {
        viewModelScope.launch { container.playlistRepository.rename(id, name) }
    }

    fun playPlaylist(playlist: Playlist) {
        val byId = _uiState.value.songs.associateBy { it.id }
        val songs = playlist.songIds.mapNotNull { byId[it] }
        if (songs.isNotEmpty()) container.playerController.setQueue(songs, autoPlay = true)
    }

    /** 从歌单的指定位置开始播放。 */
    fun playPlaylistAt(playlist: Playlist, index: Int) {
        val byId = _uiState.value.songs.associateBy { it.id }
        val songs = playlist.songIds.mapNotNull { byId[it] }
        if (songs.isEmpty()) return
        container.playerController.setQueue(songs, startIndex = index.coerceIn(songs.indices), autoPlay = true)
    }

    fun removeSongsFromPlaylist(playlistId: String, songIds: List<String>) {
        viewModelScope.launch { container.playlistRepository.removeSongs(playlistId, songIds) }
    }

    /** 点击播放队列里的某一项。 */
    fun playQueueItem(index: Int) {
        (container.playerController as? com.wxjxpp.neiro.core.player.Media3PlayerController)
            ?.playAt(index)
            ?: queue.value.getOrNull(index)?.let(container.playerController::play)
    }

    // ---- 搜索 ----

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refreshSearch()
    }

    private fun refreshSearch() {
        val state = _uiState.value
        val results = if (state.searchQuery.isBlank()) {
            emptyList()
        } else {
            state.songs.searchSongs(state.searchQuery)
        }
        _uiState.update { it.copy(searchResults = results) }
        // 关键词非空时触发在线搜索
        triggerOnlineSearch()
    }

    /** 切换在线搜索平台并重新搜索。 */
    fun setOnlineSearchPlatform(id: String) {
        if (id == _uiState.value.onlineSearchPlatform) return
        _uiState.update { it.copy(onlineSearchPlatform = id) }
        viewModelScope.launch { container.appSettings.setOnlineSearchPlatform(id) }
        triggerOnlineSearch()
    }

    fun setNeteaseCookie(cookie: String) {
        viewModelScope.launch { container.appSettings.setNeteaseCookie(cookie) }
    }

    private var onlineSearchJob: kotlinx.coroutines.Job? = null

    /** 防抖触发在线搜索：输入停顿 400ms 后执行。 */
    private fun triggerOnlineSearch() {
        onlineSearchJob?.cancel()
        val state = _uiState.value
        val query = state.searchQuery.trim()
        if (query.isEmpty()) {
            _uiState.update {
                it.copy(onlineResults = emptyList(), onlineFailedPlatforms = emptyList(), isSearchingOnline = false)
            }
            return
        }
        onlineSearchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            _uiState.update { it.copy(isSearchingOnline = true) }
            val result = runCatching {
                container.onlineSearch.search(query, state.onlineSearchPlatform)
            }.getOrElse { com.wxjxpp.neiro.core.search.OnlineSearchRepository.Result() }
            _uiState.update {
                it.copy(
                    onlineResults = result.songs,
                    onlineFailedPlatforms = result.failedPlatforms,
                    isSearchingOnline = false,
                )
            }
        }
    }

    // ---- 自定义音源 ----

    fun importUserApi(script: String) {
        viewModelScope.launch {
            runCatching { container.userApiStore.import(script) }
                .onSuccess { info -> container.activateUserApi(info.id) }
                .onFailure { error -> container.notify(error.message ?: "导入失败") }
        }
    }

    /** 从 URL 导入脚本。 */
    fun importUserApiFromUrl(url: String) {
        viewModelScope.launch {
            runCatching { container.userApiStore.importFromUrl(url) }
                .onSuccess { info -> container.activateUserApi(info.id) }
                .onFailure { error -> container.notify(error.message ?: "导入失败") }
        }
    }

    fun removeUserApi(id: String) {
        viewModelScope.launch { container.userApiStore.remove(id) }
    }

    fun activateUserApi(id: String) = container.activateUserApi(id)

    fun deactivateUserApi() = container.deactivateUserApi()

    fun updateUserApi(id: String) {
        viewModelScope.launch {
            runCatching { container.userApiStore.update(id) }
                .onSuccess { info -> container.activateUserApi(info.id) }
                .onFailure { error -> container.notify(error.message ?: "更新失败") }
        }
    }

    // ---- 内部 ----

    /** 切歌：结算上一首的收听时长，写最近播放快照，并载入新歌词。 */
    private fun onSongChanged(song: Song?) {
        val previousId = currentSongId
        if (song?.id == previousId) return

        if (previousId != null && currentSongStartedAt > 0L) {
            val listened = System.currentTimeMillis() - currentSongStartedAt
            // 少于 5 秒视为划过，不计入统计
            if (listened > 5_000L) {
                viewModelScope.launch {
                    container.statsRepository.record(
                        PlayEvent(
                            id = "pe_${System.currentTimeMillis()}",
                            songId = previousId,
                            startedAtMs = currentSongStartedAt,
                            listenedMs = listened,
                            completed = false,
                        )
                    )
                }
            }
        }

        currentSongId = song?.id
        currentSongStartedAt = if (song != null) System.currentTimeMillis() else 0L

        // 切歌瞬间先清掉上一首的歌词（避免加载期间残留旧歌词）
        _uiState.update { it.copy(lyrics = Lyrics.Empty) }
        if (song == null) return
        recordRecentSong(song)
        val requestId = ++lyricsRequestId
        viewModelScope.launch {
            val lyrics = runCatching { container.lyricsRepository.lyricsFor(song) }
                .getOrDefault(Lyrics.Empty)
            // 竞态保护：加载期间又切了歌 → 丢弃过期结果
            if (requestId == lyricsRequestId) {
                _uiState.update { it.copy(lyrics = lyrics) }
            }
        }
    }
    /** 歌词加载请求序号（防切歌竞态）。 */
    private var lyricsRequestId = 0L

    /** 最近播放：歌曲快照写入 DataStore（在线歌曲不在曲库，只能存快照）。 */
    private fun recordRecentSong(song: Song) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val json = container.appSettings.observeRecentSongsJson().first()
                val arr = org.json.JSONArray(json)
                val newList = org.json.JSONArray()
                newList.put(org.json.JSONObject(com.wxjxpp.neiro.core.serialization.SongJson.toJson(song)))
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    if (item.optString("id") != song.id && newList.length() < 50) newList.put(item)
                }
                container.appSettings.saveRecentSongsJson(newList.toString())
            }
        }
    }

    internal fun selectedSongs(): List<Song> {
        val ids = _uiState.value.selectedSongIds
        return _uiState.value.songs.filter { it.id in ids }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(container) as T
        }
    }
}