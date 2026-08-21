package com.wxjxpp.musicplayer.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wxjxpp.musicplayer.app.navigation.AppDrawerSheet
import com.wxjxpp.musicplayer.app.navigation.Destination
import com.wxjxpp.musicplayer.feature.home.EmptySongsScreen
import com.wxjxpp.musicplayer.feature.home.HomeScreen
import com.wxjxpp.musicplayer.feature.home.SelectionTopBar
import com.wxjxpp.musicplayer.feature.home.SongsTopBar
import com.wxjxpp.musicplayer.feature.placeholder.PlaceholderScreen
import com.wxjxpp.musicplayer.feature.player.PlayerBar
import com.wxjxpp.musicplayer.feature.player.PlayerDetailScreen
import com.wxjxpp.musicplayer.feature.player.QueueSheet
import com.wxjxpp.musicplayer.feature.playlist.PickPlaylistDialog
import com.wxjxpp.musicplayer.feature.playlist.PlaylistsScreen
import com.wxjxpp.musicplayer.feature.search.SearchScreen
import com.wxjxpp.musicplayer.feature.settings.SettingsScreen
import com.wxjxpp.musicplayer.feature.userapi.UserApiScreen
import com.wxjxpp.musicplayer.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 应用外壳。
 *
 * ModalNavigationDrawer（侧滑导航）
 *   └ SharedTransitionLayout
 *     └ AnimatedContent（路由切换 + 共享元素）
 *       ├ 内容区
 *       └ 播放栏（常规 or 悬浮）
 *
 * 播放栏放在壳层而非页面内，保证跨页时是同一实例，共享元素动画才连续。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicPlayerApp(container: AppContainer) {
    val viewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container))
    val uiState by viewModel.uiState.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val queue by viewModel.queue.collectAsState()

    // 全局一次性提示（音源导入失败、取流失败等）
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(Unit) {
        container.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    var route by rememberSaveable { mutableStateOf(Destination.Home.route) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val dimens = AppTheme.dimens
    val routeFadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // 运行时权限：Android 13+ 请求 READ_MEDIA_AUDIO，更低版本回退到读外部存储。
    val context = LocalContext.current
    var hasMediaPermission by remember { mutableStateOf(PermissionController.hasMediaPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasMediaPermission = result.values.all { it }
        if (hasMediaPermission) viewModel.refresh()
    }
    val requestPermission: () -> Unit = {
        permissionLauncher.launch(PermissionController.requiredMediaPermissions())
    }
    val scanLibrary: () -> Unit = {
        if (hasMediaPermission) viewModel.refresh() else requestPermission()
    }

    LaunchedEffect(hasMediaPermission) {
        if (hasMediaPermission && uiState.songs.isEmpty()) viewModel.refresh()
    }

    val inSelectionMode = uiState.selectedSongIds.isNotEmpty()

    // 返回优先级：多选态 → 抽屉 → 回歌曲页
    BackHandler(enabled = inSelectionMode || drawerState.isOpen || route != Destination.Home.route) {
        when {
            inSelectionMode -> viewModel.clearSelection()
            drawerState.isOpen -> scope.launch { drawerState.close() }
            else -> route = Destination.Home.route
        }
    }

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
        androidx.compose.material3.Scaffold(
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        ) { _ ->
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = route,
                transitionSpec = { fadeIn(routeFadeSpec) togetherWith fadeOut(routeFadeSpec) },
                label = "rootRoute",
            ) { currentRoute ->
                when (currentRoute) {
                    Destination.PlayerDetail.route -> PlayerDetailScreen(
                        state = playback,
                        lyrics = uiState.lyrics,
                        showTranslation = uiState.showTranslation,
                        queue = queue,
                        animatedVisibilityScope = this@AnimatedContent,
                        onBack = { route = Destination.Home.route },
                        onTogglePlay = viewModel::togglePlay,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onSeekFraction = viewModel::seekToFraction,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeat = viewModel::cycleRepeat,
                        onPickQueueItem = viewModel::playQueueItem,
                    )

                    else -> Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        topBar = {
                            val isSongsPage = currentRoute == Destination.Home.route ||
                                currentRoute == Destination.Library.route
                            when {
                                isSongsPage && inSelectionMode -> SelectionTopBar(
                                    selectedCount = uiState.selectedSongIds.size,
                                    onClose = viewModel::clearSelection,
                                    onSelectAll = viewModel::selectAll,
                                    onDelete = viewModel::deleteSelected,
                                    onAddToPlaylist = { showPlaylistPicker = true },
                                )

                                isSongsPage -> SongsTopBar(
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onSearch = { route = Destination.Search.route },
                                    onScan = scanLibrary,
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
                                    onOpenQueue = { showQueueSheet = true },
                                )
                            }
                        },
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            RouteContent(
                                route = currentRoute,
                                viewModel = viewModel,
                                uiState = uiState,
                                hasMediaPermission = hasMediaPermission,
                                onRequestPermission = requestPermission,
                                onScan = scanLibrary,
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
                                        onOpenQueue = { showQueueSheet = true },
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

    if (showPlaylistPicker) {
        PickPlaylistDialog(
            playlists = uiState.playlists,
            onDismiss = { showPlaylistPicker = false },
            onPick = { playlistId ->
                viewModel.addSelectedToPlaylist(playlistId)
                showPlaylistPicker = false
            },
            onCreateNew = { name ->
                viewModel.createPlaylistWithSelected(name)
                showPlaylistPicker = false
            },
        )
    }

    if (showQueueSheet) {
        QueueSheet(
            queue = queue,
            currentSongId = playback.current?.id,
            onDismiss = { showQueueSheet = false },
            onPick = { index ->
                viewModel.playQueueItem(index)
                showQueueSheet = false
            },
        )
    }
}

/** 路由内容分发。新增页面在这里加分支。 */
@Composable
private fun RouteContent(
    route: String,
    viewModel: AppViewModel,
    uiState: ShellUiState,
    hasMediaPermission: Boolean,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (route) {
        Destination.Home.route, Destination.Library.route -> if (uiState.songs.isEmpty()) {
            EmptySongsScreen(
                hasPermission = hasMediaPermission,
                isScanning = uiState.isRefreshing,
                onRequestPermission = onRequestPermission,
                onScan = onScan,
                modifier = modifier,
            )
        } else {
            HomeScreen(
                songs = uiState.songs,
                isRefreshing = uiState.isRefreshing,
                selectedIds = uiState.selectedSongIds,
                onRefresh = viewModel::refresh,
                onSongClick = { song ->
                    // 多选态下点击是切换选中，避免误触播放
                    if (uiState.selectedSongIds.isNotEmpty()) {
                        viewModel.toggleSelection(song.id)
                    } else {
                        viewModel.play(song)
                    }
                },
                onSongLongPress = { song -> viewModel.toggleSelection(song.id) },
                contentPadding = contentPadding,
            )
        }

        Destination.Search.route -> SearchScreen(
            query = uiState.searchQuery,
            localResults = uiState.searchResults,
            onlineResults = uiState.onlineResults,
            onlineFailed = uiState.onlineFailedPlatforms,
            onlinePlatforms = uiState.onlinePlatforms,
            currentOnlinePlatform = uiState.onlineSearchPlatform,
            isLoadingOnline = uiState.isSearchingOnline,
            onQueryChange = viewModel::updateSearchQuery,
            onSongClick = viewModel::play,
            onOnlinePlatformChange = viewModel::setOnlineSearchPlatform,
            contentPadding = contentPadding,
            modifier = modifier,
        )

        Destination.Playlists.route -> PlaylistsScreen(
            playlists = uiState.playlists,
            songsById = remember(uiState.songs) { uiState.songs.associateBy { it.id } },
            onCreate = viewModel::createPlaylist,
            onDelete = viewModel::deletePlaylist,
            onRename = viewModel::renamePlaylist,
            onPlay = viewModel::playPlaylist,
            onPlaySongInPlaylist = viewModel::playPlaylistAt,
            onRemoveSongs = viewModel::removeSongsFromPlaylist,
            contentPadding = contentPadding,
            modifier = modifier,
        )

        Destination.MusicSources.route -> UserApiScreen(
            apis = uiState.userApis,
            engineStatus = uiState.userApiStatus,
            onImportScript = viewModel::importUserApi,
            onImportUrl = viewModel::importUserApiFromUrl,
            onActivate = viewModel::activateUserApi,
            onDeactivate = viewModel::deactivateUserApi,
            onUpdate = viewModel::updateUserApi,
            onRemove = viewModel::removeUserApi,
            contentPadding = contentPadding,
            modifier = modifier,
        )

        Destination.Settings.route -> SettingsScreen(
            floatingPlayerBar = uiState.floatingPlayerBar,
            showTranslation = uiState.showTranslation,
            shuffleMode = uiState.shuffleMode,
            onFloatingPlayerBarChange = viewModel::setFloatingPlayerBar,
            onShowTranslationChange = viewModel::setShowTranslation,
            onShuffleModeChange = viewModel::setShuffleMode,
            contentPadding = contentPadding,
            modifier = modifier,
        )

        Destination.Diary.route -> PlaceholderScreen(
            title = "",
            description = "听歌日记：DiaryRepository 已落库，界面待接入",
            modifier = modifier,
        )

        Destination.Together.route -> PlaceholderScreen(
            title = "",
            description = "一起听：实现 TogetherTransport 后可创建 / 加入房间",
            modifier = modifier,
        )

        Destination.Report.route -> PlaceholderScreen(
            title = "",
            description = "年度报告：统计已就绪（含 24 小时分布），可视化待接入",
            modifier = modifier,
        )

        else -> PlaceholderScreen(
            title = "",
            description = "路由 $route 尚未接入页面",
            modifier = modifier,
        )
    }
}