package com.wxjxpp.musicplayer.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wxjxpp.musicplayer.feature.home.HomeScreen
import com.wxjxpp.musicplayer.feature.home.HomeTopBar
import com.wxjxpp.musicplayer.feature.player.PlayerBar
import com.wxjxpp.musicplayer.feature.player.PlayerDetailScreen
import com.wxjxpp.musicplayer.ui.theme.AppTheme

private enum class Route { Home, PlayerDetail }

/**
 * 应用根组件。
 *
 * 使用 SharedTransitionLayout + AnimatedContent，
 * 展开与收起共用同一条共享元素路径，保证动画对称。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MusicPlayerApp(viewModel: AppViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    var route by rememberSaveable { mutableStateOf(Route.Home) }
    val motion = AppTheme.motion
    val dimens = AppTheme.dimens

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                fadeIn(tween(motion.medium)) togetherWith fadeOut(tween(motion.medium))
            },
            label = "rootRoute",
        ) { current ->
            when (current) {
                Route.Home -> Scaffold(
                    topBar = { HomeTopBar(onSearch = {}) },
                    bottomBar = {
                        if (!uiState.floatingBar) {
                            PlayerBar(
                                state = playback,
                                floating = false,
                                animatedVisibilityScope = this@AnimatedContent,
                                onExpand = { route = Route.PlayerDetail },
                                onTogglePlay = viewModel::togglePlay,
                                onNext = viewModel::next,
                                onOpenQueue = {},
                            )
                        }
                    },
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        HomeScreen(
                            songs = uiState.songs,
                            isRefreshing = uiState.isRefreshing,
                            floatingBar = uiState.floatingBar,
                            onRefresh = viewModel::refresh,
                            onToggleFloatingBar = viewModel::setFloatingBar,
                            onSongClick = viewModel::play,
                            contentPadding = PaddingValues(
                                top = padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding() +
                                    dimens.playerBarHeight +
                                    dimens.spaceXl,
                            ),
                        )
                        if (uiState.floatingBar) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                PlayerBar(
                                    state = playback,
                                    floating = true,
                                    animatedVisibilityScope = this@AnimatedContent,
                                    onExpand = { route = Route.PlayerDetail },
                                    onTogglePlay = viewModel::togglePlay,
                                    onNext = viewModel::next,
                                    onOpenQueue = {},
                                )
                            }
                        }
                    }
                }

                Route.PlayerDetail -> PlayerDetailScreen(
                    state = playback,
                    animatedVisibilityScope = this@AnimatedContent,
                    onBack = { route = Route.Home },
                    onTogglePlay = viewModel::togglePlay,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onSeek = viewModel::seekTo,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeat,
                )
            }
        }
    }
}