package com.wxjxpp.musicplayer.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wxjxpp.musicplayer.core.data.RoomSongRepository
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.PlayEvent
import com.wxjxpp.musicplayer.core.model.PlaybackState
import com.wxjxpp.musicplayer.core.model.Playlist
import com.wxjxpp.musicplayer.core.model.ShuffleMode
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.search.searchSongs
import com.wxjxpp.musicplayer.core.userapi.UserApiInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    val onlinePlatforms: List<com.wxjxpp.musicplayer.core.search.OnlineSearchRepository.PlatformOption> = emptyList(),

    /** 当前歌曲的歌词。 */
    val lyrics: Lyrics = Lyrics.Empty,

    val userApis: List<UserApiInfo> = emptyList(),

    /** 音源引擎状态。 */
    val userApiStatus: com.wxjxpp.musicplayer.core.userapi.UserApiStatus? = null,

    /** 网易云 Cookie（设置页编辑）。 */
    val neteaseCookie: String = "",
)

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = container.playerController.state
    val queue: StateFlow<List<Song>> = container.playerController.queue

    /** 记录当前歌曲的开始时间，用于生成播放事件。 */
    private var currentSongStartedAt: Long = 0L
    private var currentSongId: String? = null

    init {
        container.songRepository.observeSongs()
            .onEach { songs ->
                _uiState.update { it.copy(songs = songs) }
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
            .onEach { status -> _uiState.update { it.copy(userApiStatus = status) } }
            .launchIn(viewModelScope)

        // 网易云 Cookie
        container.appSettings.observeNeteaseCookie()
            .onEach { cookie -> _uiState.update { it.copy(neteaseCookie = cookie) } }
            .launchIn(viewModelScope)
        // 切歌时记录上一首的播放事件并加载新歌词
        playbackState
            .onEach { state -> onSongChanged(state.current) }
            .launchIn(viewModelScope)
    }

    // ---- 曲库 ----

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            runCatching { container.songRepository.rescanLocal() }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // ---- 设置 ----

    fun setFloatingPlayerBar(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setFloatingPlayerBar(enabled) }
    }

    fun setShuffleMode(mode: ShuffleMode) {
        viewModelScope.launch { container.appSettings.setShuffleMode(mode) }
    }

    fun setShowTranslation(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setShowTranslation(enabled) }
    }

    // ---- 播放 ----

    fun play(song: Song) = container.playerController.play(song)
    fun togglePlay() = container.playerController.togglePlay()
    fun next() = container.playerController.next()
    fun previous() = container.playerController.previous()
    fun toggleShuffle() = container.playerController.toggleShuffle()
    fun cycleRepeat() = container.playerController.cycleRepeatMode()

    fun seekToFraction(fraction: Float) {
        val duration = playbackState.value.durationMs
        container.playerController.seekTo((duration * fraction).toLong())
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

    fun addSelectedToPlaylist(playlistId: String) {
        val ids = _uiState.value.selectedSongIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            container.playlistRepository.addSongs(playlistId, ids)
            clearSelection()
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
        (container.playerController as? com.wxjxpp.musicplayer.core.player.Media3PlayerController)
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
            }.getOrElse { com.wxjxpp.musicplayer.core.search.OnlineSearchRepository.Result() }
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

    /** 切歌：结算上一首的收听时长，并载入新歌词。 */
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

        if (song == null) {
            _uiState.update { it.copy(lyrics = Lyrics.Empty) }
            return
        }
        viewModelScope.launch {
            val lyrics = runCatching { container.lyricsRepository.lyricsFor(song) }
                .getOrDefault(Lyrics.Empty)
            _uiState.update { it.copy(lyrics = lyrics) }
        }
    }

    private fun selectedSongs(): List<Song> {
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