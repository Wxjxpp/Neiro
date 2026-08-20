package com.wxjxpp.musicplayer.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxjxpp.musicplayer.core.data.FakeSongRepository
import com.wxjxpp.musicplayer.core.data.SongRepository
import com.wxjxpp.musicplayer.core.model.PlaybackState
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.player.FakePlayerController
import com.wxjxpp.musicplayer.core.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val songs: List<Song> = emptyList(),
    val isRefreshing: Boolean = false,
    val floatingBar: Boolean = true,
)

/**
 * 应用状态入口。依赖通过构造参数注入，后续换成 Hilt 不需要改 UI。
 */
class AppViewModel(
    private val repository: SongRepository = FakeSongRepository(),
    private val player: PlayerController = FakePlayerController(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = player.state

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val songs = repository.loadSongs()
            player.setQueue(songs)
            _uiState.update { it.copy(songs = songs, isRefreshing = false) }
        }
    }

    fun setFloatingBar(enabled: Boolean) {
        _uiState.update { it.copy(floatingBar = enabled) }
    }

    fun play(song: Song) = player.play(song)
    fun togglePlay() = player.togglePlay()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(progress: Float) = player.seekTo(progress)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeatMode()
}