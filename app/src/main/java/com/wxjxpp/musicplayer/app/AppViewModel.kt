package com.wxjxpp.musicplayer.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wxjxpp.musicplayer.core.model.PlaybackState
import com.wxjxpp.musicplayer.core.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 全局壳层状态：曲库列表、刷新态、播放栏样式、抽屉开关。
 *
 * 页面级状态请各自建 ViewModel，不要都堆到这里。
 */
data class ShellUiState(
    val songs: List<Song> = emptyList(),
    val isRefreshing: Boolean = false,
    val floatingPlayerBar: Boolean = true,
)

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = container.playerController.state

    val queue: StateFlow<List<Song>> = container.playerController.queue

    init {
        container.songRepository.observeSongs()
            .onEach { songs ->
                _uiState.update { it.copy(songs = songs) }
                if (queue.value.isEmpty()) {
                    container.playerController.setQueue(songs, autoPlay = false)
                }
            }
            .launchIn(viewModelScope)

        container.settingsRepository.observeFloatingPlayerBar()
            .onEach { enabled -> _uiState.update { it.copy(floatingPlayerBar = enabled) } }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            runCatching { container.songRepository.rescanLocal() }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun setFloatingPlayerBar(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setFloatingPlayerBar(enabled) }
    }

    // 播放操作统一转发给 PlayerController，UI 不直接接触播放引擎
    fun play(song: Song) = container.playerController.play(song)
    fun togglePlay() = container.playerController.togglePlay()
    fun next() = container.playerController.next()
    fun previous() = container.playerController.previous()
    fun toggleShuffle() = container.playerController.toggleShuffle()
    fun cycleRepeat() = container.playerController.cycleRepeatMode()

    /** UI 用 0f..1f 表达进度，这里换算成毫秒再下发。 */
    fun seekToFraction(fraction: Float) {
        val duration = playbackState.value.durationMs
        container.playerController.seekTo((duration * fraction).toLong())
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(container) as T
        }
    }
}