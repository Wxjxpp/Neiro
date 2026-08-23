package com.wxjxpp.neiro.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.calculateTopPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.app.navigation.AppDrawerSheet
import com.wxjxpp.neiro.app.navigation.Destination
import com.wxjxpp.neiro.feature.albums.AlbumSortField
import com.wxjxpp.neiro.feature.albums.AlbumsScreen
import com.wxjxpp.neiro.feature.discover.DiscoverScreen
import com.wxjxpp.neiro.feature.home.EmptySongsScreen
import com.wxjxpp.neiro.feature.home.HomeScreen
import com.wxjxpp.neiro.feature.home.SelectionTopBar
import com.wxjxpp.neiro.feature.home.SongsTopBar
import com.wxjxpp.neiro.feature.placeholder.PlaceholderScreen
import com.wxjxpp.neiro.feature.player.PlayerBar
import com.wxjxpp.neiro.feature.player.PlayerDetailScreen
import com.wxjxpp.neiro.feature.diary.DiaryScreen
import com.wxjxpp.neiro.feature.player.QueueSheet
import com.wxjxpp.neiro.feature.playlist.PickPlaylistDialog
import com.wxjxpp.neiro.feature.playlist.PlaylistsScreen
import com.wxjxpp.neiro.feature.search.SearchScreen
import com.wxjxpp.neiro.feature.settings.SettingsScreen
import com.wxjxpp.neiro.feature.userapi.UserApiScreen
import com.wxjxpp.neiro.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 应用外壳。
 *
 * 层级模型（返回手势永远只回退一层）：
 *   底层页面（侧边栏可开）→ 播放页 Sheet（进度 0→1）→ 歌词页（进度 1→2）
 *
 * 关键行为：
 * - **拖拽跟手**：播放页/歌词页是一条连续的 sheetProgress（0=收起 1=播放页 2=歌词页），
 *   手指停在哪里 Sheet 就停在哪里；松手后动画收敛到最近锚点。播放页展开期间底层页面
 *   不卸载，只是 Z 轴下沉 + 缩放 + 渐暗（真实深度感）。
 * - **抽屉**：滑出瞬间拦截返回手势（BackHandler enabled = drawerState.targetValue != Closed），
 *   底层页面同样做 Z 轴下沉。
 * - **错误横幅**：取流失败等报错在顶部弹出（安全区内），带关闭按钮 + 上滑关闭。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicPlayerApp(container: AppContainer) {
    val viewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container))
    val uiState by viewModel.uiState.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val queue by viewModel.queue.collectAsState()
    // Sheet 进度：0 收起 / 1 播放页 / 2 歌词页。直接驱动渲染层，天然跟手。
    val rawProgress by viewModel.sheetProgress.collectAsState()
    val density = LocalDensity.current

    fun snapTargetOf(progress: Float): Float = when {
        progress >= 1.5f -> 2f
        progress >= 0.5f -> 1f
        else -> 0f
    }

    /** 程序化打开/关闭：写入目标进度，由下面的 animateFloatAsState 平滑过渡。 */
    var programmaticTarget by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val sheetProgress by animateFloatAsState(
        targetValue = if (dragging) rawProgress else programmaticTarget,
        animationSpec = tween(320),
        label = "sheetProgress",
    )
    // 拖拽结束：把当前进度收敛到最近的锚点（0/1/2）
    LaunchedEffect(rawProgress, dragging) {
        if (!dragging && rawProgress != programmaticTarget) {
            programmaticTarget = snapTargetOf(rawProgress)
        }
    }
    // 程序化目标变化时同步底层值（避免拖拽中途切换目标错乱）
    LaunchedEffect(programmaticTarget) {
        if (!dragging) viewModel.setSheetProgress(programmaticTarget)
    }

    val hasSong = playback.current != null
    val playerOpen = sheetProgress > 0.01f
    val lyricsOpen = sheetProgress > 1.01f

    // 全局一次性提示（音源导入失败等轻提示保留 Snackbar）
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(Unit) {
        container.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    var route by rememberSaveable { mutableStateOf(Destination.Home.route) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val dimens = AppTheme.dimens

    // 弹层状态（提前声明，供播放栏等回调引用）
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

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

    // 曲库已持久化到 Room：启动时只读数据库展示，不自动重扫。
    LaunchedEffect(hasMediaPermission) {
        if (hasMediaPermission && uiState.songs.isEmpty() && !viewModel.hasScannedOnce) {
            viewModel.hasScannedOnce = true
            viewModel.refresh()
        }
    }

    val inSelectionMode = uiState.selectedSongIds.isNotEmpty()
    // 进入听歌日记页时加载热力图数据
    LaunchedEffect(route) {
        if (route == Destination.Diary.route) viewModel.loadHeatmap()
        if (route == Destination.Discover.route && uiState.discoverSections.isEmpty()) {
            viewModel.loadDiscover()
        }
    }

    /**
     * 返回优先级（严格单层回退，绝不跳级）：
     * 多选态 → 错误横幅不拦截 → 歌词页(2→1) → 播放页(1→0) → 抽屉 → 根页面回首页
     */
    BackHandler(enabled = inSelectionMode || lyricsOpen || playerOpen || drawerState.isOpen || route != Destination.Home.route) {
        when {
            inSelectionMode -> viewModel.clearSelection()
            lyricsOpen -> programmaticTarget = 1f          // 歌词页 → 播放页
            playerOpen -> programmaticTarget = 0f          // 播放页 → 播放栏
            drawerState.isOpen -> scope.launch { drawerState.close() }
            route != Destination.Home.route -> route = Destination.Home.route
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 播放详情页禁用抽屉手势：歌词左右滑动防误触；播放页半开状态也不允许
        gesturesEnabled = !playerOpen,
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
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // ---- 底层内容：始终组合（不被播放页卸载），随 Sheet/抽屉做 Z 轴深度变换 ----
            val depthScale by animateFloatAsState(
                targetValue = when {
                    drawerState.isOpen || drawerState.isAnimationRunning && drawerState.targetValue == DrawerValue.Open -> 0.92f
                    playerOpen -> 0.94f
                    else -> 1f
                },
                animationSpec = tween(300),
                label = "depthScale",
            )
            val depthAlpha by animateFloatAsState(
                targetValue = when {
                    drawerState.isOpen || drawerState.isAnimationRunning && drawerState.targetValue == DrawerValue.Open -> 0.5f
                    playerOpen -> 0.6f
                    else -> 1f
                },
                animationSpec = tween(300),
                label = "depthAlpha",
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = depthScale
                        scaleY = depthScale
                        alpha = depthAlpha
                        // 以屏幕中心为原点缩放，产生向 Z 轴深处退去的感觉
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.42f)
                    },
            ) {
                androidx.compose.material3.Scaffold(
                    snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
                ) { scaffoldPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        RouteContent(
                            route = route,
                            viewModel = viewModel,
                            uiState = uiState,
                            hasMediaPermission = hasMediaPermission,
                            onRequestPermission = requestPermission,
                            onScan = scanLibrary,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onNavigate = { target -> route = target },
                            onAddSelectedToPlaylist = { showPlaylistPicker = true },
                            contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding()),
                            modifier = Modifier.fillMaxSize(),
                        )
                        // 浮动播放栏（有歌且播放页未展开时可见）
                        if (hasSong && uiState.floatingPlayerBar) {
                            AnimatedVisibility(
                                visible = !playerOpen,
                                enter = slideInVertically(tween(280)) { it } + fadeIn(tween(280)),
                                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
                                modifier = Modifier.align(Alignment.BottomCenter),
                            ) {
                                PlayerBar(
                                    state = playback,
                                    floating = true,
                                    onExpand = { programmaticTarget = 1f },
                                    onTogglePlay = viewModel::togglePlay,
                                    onNext = viewModel::next,
                                    onOpenQueue = { showQueueSheet = true },
                                    // 上滑跟手：把手指位移映射为 Sheet 进度（0→1）
                                    onDragProgress = { deltaPx ->
                                        dragging = true
                                        val step = with(density) { deltaPx.toDp() } / dimens.playerBarHeight
                                        viewModel.setSheetProgress((rawProgress - step.value).coerceIn(0f, 2f))
                                    },
                                    onDragEnd = {
                                        dragging = false
                                        programmaticTarget = snapTargetOf(rawProgress)
                                    },
                                )
                            }
                        }
                        // 顶部错误横幅（安全区内，可关闭 + 上滑关闭）
                        ErrorBanner(
                            message = uiState.errorMessage,
                            onDismiss = viewModel::dismissError,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }

            // ---- 播放页/歌词页 Sheet：一条连续进度驱动，拖拽全程跟手 ----
            if (hasSong) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = size.height * (1f - (sheetProgress / 2f).coerceIn(0f, 1f))
                            alpha = (sheetProgress * 8f).coerceIn(0f, 1f)
                        },
                ) {
                    PlayerDetailScreen(
                        state = playback,
                        lyrics = uiState.lyrics,
                        showTranslation = uiState.showTranslation,
                        lyricsOffsetMs = uiState.lyricsOffsetMs,
                        ambientGlow = uiState.ambientGlow,
                        queue = queue,
                        lyricsAlign = uiState.lyricsAlign,
                        springLyrics = uiState.labSpringLyrics,
                        lyricsFontScale = uiState.lyricsFontScale,
                        lyricsGapScale = uiState.lyricsGapScale,
                        pureModeDefault = uiState.pureModeDefault,
                        sheetProgress = sheetProgress,
                        onCollapseToPlayer = { programmaticTarget = 1f },
                        onExpandLyrics = { programmaticTarget = 2f },
                        onDrag = { deltaPx ->
                            dragging = true
                            val step = with(density) { deltaPx.toDp() } / (dimens.playerBarHeight * 1.4f)
                            viewModel.setSheetProgress((rawProgress - step.value).coerceIn(0f, 2f))
                        },
                        onDragEnd = {
                            dragging = false
                            programmaticTarget = snapTargetOf(rawProgress)
                        },
                        onTogglePlay = viewModel::togglePlay,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onSeekFraction = viewModel::seekToFraction,
                        onSeekTo = viewModel::seekTo,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeat = viewModel::cycleRepeat,
                        onPickQueueItem = viewModel::playQueueItem,
                        onLyricsOffsetChange = viewModel::setLyricsOffset,
                        onMatchLyrics = viewModel::matchLyricsOnline,
                        onToggleTranslation = { viewModel.setShowTranslation(!uiState.showTranslation) },
                        onSpeedChange = viewModel::setSpeed,
                        currentQuality = uiState.preferredQuality,
                        onQualityChange = viewModel::setPreferredQuality,
                    )
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

/** 顶部错误横幅：顶部弹出、左上角关闭按钮、上滑关闭。 */
@Composable
private fun ErrorBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues()
    androidx.compose.animation.AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(260)),
        exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(180)),
        modifier = modifier.padding(top = statusBar.calculateTopPadding()),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.dimens.spaceLg)
                // 上滑关闭
                .pointerInput(onDismiss) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        if (dragAmount < -60f) onDismiss()
                    }
                },
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = message.orEmpty(),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "errorMessage",
            ) { text ->
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = AppTheme.dimens.spaceMd),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 4,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = AppTheme.dimens.spaceSm),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
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
    onOpenDrawer: () -> Unit,
    onNavigate: (String) -> Unit,
    onAddSelectedToPlaylist: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // 页面间切换：淡入淡出（Expressive motionScheme 由主题统一下发节奏）
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) togetherWith
                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
        },
        label = "rootRoute",
        modifier = modifier,
    ) { currentRoute ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentRoute) {
                Destination.Home.route, Destination.Library.route -> if (uiState.songs.isEmpty()) {
                    EmptySongsScreen(
                        hasPermission = hasMediaPermission,
                        isScanning = uiState.isRefreshing,
                        onRequestPermission = onRequestPermission,
                        onScan = onScan,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    HomeScreen(
                        songs = uiState.songs,
                        isRefreshing = uiState.isRefreshing,
                        selectedIds = uiState.selectedSongIds,
                        topBar = {
                            if (inSelectionModeCompat(uiState)) {
                                SelectionTopBar(
                                    selectedCount = uiState.selectedSongIds.size,
                                    onClose = viewModel::clearSelection,
                                    onSelectAll = viewModel::selectAll,
                                    onDelete = viewModel::deleteSelected,
                                    onAddToPlaylist = onAddSelectedToPlaylist,
                                )
                            } else {
                                SongsTopBar(
                                    onOpenDrawer = onOpenDrawer,
                                    onSearch = { onNavigate(Destination.Search.route) },
                                    onScan = onScan,
                                    onPlayRandom = { viewModel.playRandom() },
                                    sortField = uiState.songSortField,
                                    sortDescending = uiState.songSortDescending,
                                    onSortFieldChange = viewModel::setSongSortField,
                                    onSortDirectionToggle = { viewModel.setSongSortDescending(!uiState.songSortDescending) },
                                )
                            }
                        },
                        onRefresh = viewModel::refresh,
                        onSongClick = { song ->
                            if (uiState.selectedSongIds.isNotEmpty()) {
                                viewModel.toggleSelection(song.id)
                            } else {
                                viewModel.play(song)
                            }
                        },
                        onSongLongPress = { song -> viewModel.toggleSelection(song.id) },
                        onRemoveFromLibrary = { song -> viewModel.removeSongFromLibrary(song.id) },
                        onRequestDeleteFile = { song, launchConfirm ->
                            viewModel.requestDeleteFile(song, onNeedSystemConfirm = launchConfirm, onFinalize = {})
                        },
                        onFinalizeDeleteFile = { songId -> viewModel.finalizeFileDeleted(songId) },
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Destination.Albums.route -> AlbumsScreen(
                    songs = uiState.songs,
                    sortField = uiState.albumSortField,
                    sortDescending = uiState.albumSortDescending,
                    onSortFieldChange = viewModel::setAlbumSortField,
                    onSortDirectionToggle = { viewModel.setAlbumSortDescending(!uiState.albumSortDescending) },
                    onOpenDrawer = onOpenDrawer,
                    onPlayAlbum = { album -> album.songs.firstOrNull()?.let(viewModel::play) },
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Discover.route -> DiscoverScreen(
                    sections = uiState.discoverSections,
                    isLoading = uiState.isDiscoverLoading,
                    onOpenDrawer = onOpenDrawer,
                    onSongClick = viewModel::play,
                    onPlaySection = viewModel::playSection,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Search.route -> SearchScreen(
                    query = uiState.searchQuery,
                    localResults = uiState.searchResults,
                    onlineResults = uiState.onlineResults,
                    onlineFailed = uiState.onlineFailedPlatforms,
                    onlinePlatforms = uiState.onlinePlatforms,
                    currentOnlinePlatform = uiState.onlineSearchPlatform,
                    isLoadingOnline = uiState.isSearchingOnline,
                    noSourceAvailable = uiState.onlinePlatforms.size <= 1 &&
                        uiState.userApiStatus !is com.wxjxpp.neiro.core.userapi.UserApiStatus.Ready,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSongClick = viewModel::play,
                    onDownloadSong = viewModel::downloadSong,
                    onDownloadLyrics = viewModel::downloadLyrics,
                    onOnlinePlatformChange = viewModel::setOnlineSearchPlatform,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
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
                    onOpenDrawer = onOpenDrawer,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Settings.route -> SettingsScreen(
                    floatingPlayerBar = uiState.floatingPlayerBar,
                    showTranslation = uiState.showTranslation,
                    shuffleMode = uiState.shuffleMode,
                    neteaseCookie = uiState.neteaseCookie,
                    lyricsOffsetMs = uiState.lyricsOffsetMs,
                    pauseOnHeadphoneDisconnect = uiState.pauseOnHeadphoneDisconnect,
                    pauseOnAudioFocusLoss = uiState.pauseOnAudioFocusLoss,
                    ambientGlow = uiState.ambientGlow,
                    onFloatingPlayerBarChange = viewModel::setFloatingPlayerBar,
                    onShowTranslationChange = viewModel::setShowTranslation,
                    onShuffleModeChange = viewModel::setShuffleMode,
                    onNeteaseCookieChange = viewModel::setNeteaseCookie,
                    onLyricsOffsetChange = viewModel::setLyricsOffset,
                    onPauseOnHeadphoneDisconnectChange = viewModel::setPauseOnHeadphoneDisconnect,
                    onPauseOnAudioFocusLossChange = viewModel::setPauseOnAudioFocusLoss,
                    onAmbientGlowChange = viewModel::setAmbientGlow,
                    lyricsAlign = uiState.lyricsAlign,
                    lyricsFontScale = uiState.lyricsFontScale,
                    lyricsGapScale = uiState.lyricsGapScale,
                    labSpringLyrics = uiState.labSpringLyrics,
                    onLyricsAlignChange = viewModel::setLyricsAlign,
                    onLyricsFontScaleChange = viewModel::setLyricsFontScale,
                    onLyricsGapScaleChange = viewModel::setLyricsGapScale,
                    pureModeDefault = uiState.pureModeDefault,
                    onPureModeDefaultChange = viewModel::setPureModeDefault,
                    lab8Bit = uiState.lab8Bit,
                    onLab8BitChange = viewModel::setLab8Bit,
                    labTurboSpeed = uiState.labTurboSpeed,
                    onLabTurboSpeedChange = viewModel::setLabTurboSpeed,
                    resumeOnStart = uiState.resumeOnStart,
                    onResumeOnStartChange = viewModel::setResumeOnStart,
                    autoPlayOnStart = uiState.autoPlayOnStart,
                    onAutoPlayOnStartChange = viewModel::setAutoPlayOnStart,
                    preferredQuality = uiState.preferredQuality,
                    onPreferredQualityChange = viewModel::setPreferredQuality,
                    qualityFallbackDirection = uiState.qualityFallbackDirection,
                    onQualityFallbackDirectionChange = viewModel::setQualityFallbackDirection,
                    appFontScale = uiState.appFontScale,
                    onAppFontScaleChange = viewModel::setAppFontScale,
                    appFontFamily = uiState.appFontFamily,
                    onAppFontFamilyChange = viewModel::setAppFontFamily,
                    onLabSpringLyricsChange = viewModel::setLabSpringLyrics,
                    onOpenDrawer = onOpenDrawer,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Diary.route -> DiaryScreen(
                    days = uiState.heatmapDays,
                    isLoading = uiState.isHeatmapLoading,
                    onOpenDrawer = onOpenDrawer,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Together.route -> PlaceholderScreen(
                    title = "",
                    description = "一起听：实现 TogetherTransport 后可创建 / 加入房间",
                    onOpenDrawer = onOpenDrawer,
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Report.route -> PlaceholderScreen(
                    title = "",
                    description = "年度报告：统计已就绪（含 24 小时分布），可视化待接入",
                    onOpenDrawer = onOpenDrawer,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> PlaceholderScreen(
                    title = "",
                    description = "路由 $currentRoute 尚未接入页面",
                    onOpenDrawer = onOpenDrawer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun inSelectionModeCompat(uiState: ShellUiState): Boolean = uiState.selectedSongIds.isNotEmpty()