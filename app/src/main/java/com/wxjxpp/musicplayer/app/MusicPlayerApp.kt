package com.wxjxpp.musicplayer.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wxjxpp.musicplayer.app.navigation.AppDrawerSheet
import com.wxjxpp.musicplayer.app.navigation.Destination
import com.wxjxpp.musicplayer.feature.home.HomeScreen
import com.wxjxpp.musicplayer.feature.home.EmptySongsScreen
import com.wxjxpp.musicplayer.feature.home.SongsTopBar
import com.wxjxpp.musicplayer.feature.placeholder.PlaceholderScreen
import com.wxjxpp.musicplayer.feature.settings.SettingsScreen
import com.wxjxpp.musicplayer.feature.player.PlayerBar
import com.wxjxpp.musicplayer.feature.player.PlayerDetailScreen
import com.wxjxpp.musicplayer.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 应用外壳。
 *
 * 结构：ModalNavigationDrawer（侧滑菜单）
 *      └ SharedTransitionLayout
 *        └ AnimatedContent（路由切换 + 共享元素）
 *          ├ 内容区（首页 / 各功能页）
 *          └ 播放栏（常规 or 悬浮）
 *
 * 播放栏放在壳层而非各页面内，保证跨页面时它是同一个实例，
 * 共享元素动画才能连续。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicPlayerApp(container: AppContainer) {
    val viewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container))
    val uiState by viewModel.uiState.collectAsState()
    val playback by viewModel.playbackState.collectAsState()

    var route by rememberSaveable { mutableStateOf(Destination.Home.route) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val dimens = AppTheme.dimens
    // motionScheme 是 @Composable 取值，不能在 transitionSpec lambda 里读，先在这里取出
    val routeFadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    BackHandler(enabled = route != Destination.Home.route || drawerState.isOpen) { if (drawerState.isOpen) scope.launch { drawerState.close() } else route = Destination.Home.route }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerSheet(
                currentRoute = route,
                onNavigate = { destination ->
                    route = destination.route
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    fadeIn(routeFadeSpec) togetherWith fadeOut(routeFadeSpec)
                },
                label = "rootRoute",
            ) { currentRoute ->
                when (currentRoute) {
                    Destination.PlayerDetail.route -> PlayerDetailScreen(
                        state = playback,
                        animatedVisibilityScope = this@AnimatedContent,
                        onBack = { route = Destination.Home.route },
                        onTogglePlay = viewModel::togglePlay,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onSeekFraction = viewModel::seekToFraction,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeat = viewModel::cycleRepeat,
                    )

                    else -> Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        topBar = {
                            if (currentRoute == Destination.Home.route || currentRoute == Destination.Library.route) {
                                SongsTopBar(
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onSearch = { route = Destination.Search.route },
                                )
                            }
                        },
                        bottomBar = {
                            if (!uiState.floatingPlayerBar) {
                                PlayerBar(
                                    state = playback,
                                    floating = false,
                                    animatedVisibilityScope = this@AnimatedContent,
                                    onExpand = { route = Destination.PlayerDetail.route },
                                    onTogglePlay = viewModel::togglePlay,
                                    onNext = viewModel::next,
                                    onOpenQueue = {},
                                )
                            }
                        },
                    ) { padding ->
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            RouteContent(
                                route = currentRoute,
                                viewModel = viewModel,
                                uiState = uiState,
                                contentPadding = PaddingValues(
                                    top = padding.calculateTopPadding(),
                                    bottom = padding.calculateBottomPadding() +
                                        dimens.playerBarHeight +
                                        dimens.spaceXl,
                                ),
                            )
                            if (uiState.floatingPlayerBar) {
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
                                        onExpand = { route = Destination.PlayerDetail.route },
                                        onTogglePlay = viewModel::togglePlay,
                                        onNext = viewModel::next,
                                        onOpenQueue = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 路由内容分发。
 *
 * 新增页面在这里加一个分支即可；占位页保证导航链路先跑通。
 */
@Composable
private fun RouteContent(
    route: String,
    viewModel: AppViewModel,
    uiState: ShellUiState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (route) {
        Destination.Home.route, Destination.Library.route -> if (uiState.songs.isEmpty()) EmptySongsScreen() else HomeScreen(
            songs = uiState.songs,
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            onSongClick = viewModel::play,
            contentPadding = contentPadding,
        )

        // 搜索由歌曲页入口进入；页面顶部不再重复显示老式标题。
        Destination.Search.route -> PlaceholderScreen(
            title = "",
            description = "搜索功能接入 MusicSource.search 后展示多音源聚合结果",
            modifier = modifier,
        )

        Destination.Playlists.route -> PlaceholderScreen(
            title = "歌单",
            description = "PlaylistRepository 已就绪，接入后展示歌单列表与详情",
            modifier = modifier,
        )

        Destination.Diary.route -> PlaceholderScreen(
            title = "听歌日记",
            description = "DiaryRepository 已就绪，接入后按日期展示听歌记录与随笔",
            modifier = modifier,
        )

        Destination.Together.route -> PlaceholderScreen(
            title = "一起听",
            description = "实现 TogetherTransport 后在此创建 / 加入房间",
            modifier = modifier,
        )

        Destination.Report.route -> PlaceholderScreen(
            title = "年度报告",
            description = "StatsRepository.report 已就绪，接入后生成可视化报告",
            modifier = modifier,
        )

        Destination.Settings.route -> SettingsScreen(
            floatingPlayerBar = uiState.floatingPlayerBar,
            onFloatingPlayerBarChange = viewModel::setFloatingPlayerBar,
            modifier = modifier,
        )

        else -> PlaceholderScreen(
            title = "未实现",
            description = "路由 $route 尚未接入页面",
            modifier = modifier,
        )
    }
}