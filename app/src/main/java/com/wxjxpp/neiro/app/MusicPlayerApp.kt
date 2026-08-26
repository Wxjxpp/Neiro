@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.app.navigation.AppDrawerSheet
import com.wxjxpp.neiro.feature.together.TogetherScreen
import com.wxjxpp.neiro.core.together.LitTogetherTransport
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
import com.wxjxpp.neiro.feature.favorites.FavoritesScreen
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
    val scope = rememberCoroutineScope()
    /**
     * 播放页位移引擎（像素级，非 Sheet 进度）：
     * - offsetFraction：0=收起（仅播放栏） 1=播放页全开 2=歌词页全开；
     * - 拖拽：手指动多少像素，页面就位移多少像素（1:1 跟手，无任何映射/动画介入）；
     * - 松手：只允许收敛到【相邻】锚点——从收起最多到播放页，从播放页最多到歌词页，
     *   物理上不可能"一把拉到歌词页"；本帧位移方向参与判定（甩动手势）；
     * - 程序化（点按钮/返回键）：Animatable 平滑动画到目标锚点。
     */
    val density = LocalDensity.current
    val offsetAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var lastDragDelta by remember { mutableStateOf(0f) }
    val offsetFraction = offsetAnim.value
    val playerOpen = offsetFraction > 0.02f
    val lyricsOpen = offsetFraction > 1.5f
    /** 程序化跳转到锚点（带动画）。 */
    fun animateTo(target: Float) {
        scope.launch { offsetAnim.animateTo(target, tween(300)) }
    }
    /** 松手收敛：只允许移动到【相邻】锚点（±1），甩动手势可强化方向选择。 */
    fun settleAfterDrag() {
        val cur = offsetAnim.value
        val lower = cur.toInt().coerceIn(0, 1)          // 下邻锚点
        val upper = (lower + 1).coerceAtMost(2)         // 上邻锚点（最多 +1，绝无跨级）
        var target = if (cur - lower > upper - cur) upper else lower
        // 最后一帧位移较大 = 甩动：顺着手势方向去相邻锚点
        if (lastDragDelta < -10f && target < upper) target = upper
        if (lastDragDelta > 10f && target > lower) target = lower
        dragging = false
        lastDragDelta = 0f
        animateTo(target.toFloat())
    }
    /** 拖拽中：像素 1:1 跟手（deltaPx 为本帧位移，向上为负；1 屏高度 = 1 单位）。 */
    val screenHdp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    fun onDragDelta(deltaPx: Float) {
        if (!dragging) dragging = true
        lastDragDelta = deltaPx
        val dpDelta = with(density) { deltaPx.toDp() }.value
        scope.launch {
            offsetAnim.snapTo((offsetAnim.value - dpDelta / screenHdp.coerceAtLeast(1)).coerceIn(0f, 2f))
        }
    }

    val hasSong = playback.current != null

    // 全局一次性提示（音源导入失败等）：统一路由到顶部横幅（中性样式）
    LaunchedEffect(Unit) {
        container.messages.collect { message -> container.notifyInfo(message) }
    }

    var route by rememberSaveable { mutableStateOf(Destination.Discover.route) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val dimens = AppTheme.dimens

    // 弹层状态（提前声明，供播放栏等回调引用）
    var showBatchSheet by remember { mutableStateOf(false) }
    /** 批量操作目标歌曲（首页多选 or 搜索页在线多选）。 */
    var batchSongs by remember { mutableStateOf<List<com.wxjxpp.neiro.core.model.Song>>(emptyList()) }
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
     * 多选态 → 错误横幅不拦截 → 歌词页(2→1) → 播放页(1→0) → 抽屉 → 根页面回首页。
     * 例外：发现页不拦截返回手势——冷启动即发现页，按返回应直接退出到桌面，
     * 而不是被劫持回歌曲页/首页。
     */
    BackHandler(
        enabled = inSelectionMode || lyricsOpen || playerOpen || drawerState.isOpen ||
            (route != Destination.Home.route && route != Destination.Discover.route),
    ) {
        when {
            inSelectionMode -> viewModel.clearSelection()
            lyricsOpen -> animateTo(1f)                    // 歌词页 → 播放页
            playerOpen -> animateTo(0f)                    // 播放页 → 播放栏
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
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .graphicsLayer {
                        scaleX = depthScale
                        scaleY = depthScale
                        alpha = depthAlpha
                        // 以屏幕中心为原点缩放，产生向 Z 轴深处退去的感觉
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.42f)
                    },
            ) {
                androidx.compose.material3.Scaffold(
                    // 顶部横幅栈：成功绿 / 信息中性 / 错误红，堆叠展示
                    topBar = {
                        com.wxjxpp.neiro.ui.components.BannerStack(
                            banners = uiState.banners,
                            onDismiss = viewModel::dismissBanner,
                        )
                    },
                ) { scaffoldPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        RouteContent(
                            route = route,
                            container = container,
                            viewModel = viewModel,
                            uiState = uiState,
                            hasMediaPermission = hasMediaPermission,
                            onRequestPermission = requestPermission,
                            onScan = scanLibrary,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onNavigate = { target -> route = target },
                            onToast = { message -> container.notifyInfo(message) },
                            currentPlayingId = playback.current?.id,
                            onBatchOperate = { songs ->
                                batchSongs = songs
                                showBatchSheet = true
                            },
                            onAddSelectedToPlaylist = {
                                batchSongs = viewModel.selectedSongs()
                                showBatchSheet = true
                            },
                            contentPadding = PaddingValues(
                                // 预留播放栏高度：最后一项不再被浮动播放栏遮住。
                                // 注意不要加 top——外层 Column 已做 statusBars padding，加了会双倍
                                bottom = scaffoldPadding.calculateBottomPadding() +
                                    AppTheme.dimens.playerBarHeight +
                                    AppTheme.dimens.floatingBarBottomMargin + 12.dp,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                        // 底栏联动：字母索引拖球快移等页面手势请求播放栏下沉出屏/回归
                        var bottomBarSunken by remember { mutableStateOf(false) }
                        val barSink: (Boolean) -> Unit = { sunken -> bottomBarSunken = sunken }
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.wxjxpp.neiro.ui.components.LocalBottomBarSink provides barSink,
                        ) {
                        // 播放栏：floating=浮动样式；关闭开关后仍显示（底部贴合的紧凑条）
                        if (hasSong) {
                            val sinkOffset = with(density) { AppTheme.dimens.playerBarHeight.toPx() } +
                                with(density) { AppTheme.dimens.floatingBarBottomMargin.toPx() }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !playerOpen,
                                enter = slideInVertically(tween(280)) { it } + fadeIn(tween(280)),
                                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
                                modifier = Modifier.align(Alignment.BottomCenter),
                            ) {
                                androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = if (bottomBarSunken) 1f else 0f,
                                    animationSpec = tween(220),
                                    label = "barSink",
                                ).let { sink ->
                                    PlayerBar(
                                        state = playback,
                                        floating = uiState.floatingPlayerBar,
                                        onExpand = { animateTo(1f) },
                                        onTogglePlay = viewModel::togglePlay,
                                        onNext = viewModel::next,
                                        onOpenQueue = { showQueueSheet = true },
                                        onDragProgress = { deltaPx -> onDragDelta(deltaPx) },
                                        onDragEnd = { settleAfterDrag() },
                                        modifier = Modifier.graphicsLayer {
                                            translationY = sink.value * sinkOffset
                                        },
                                    )
                                }
                            }
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

            // ---- 播放页/歌词页：像素级偏移，拖拽 1:1 跟手 ----
            if (hasSong) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // 锚点语义：0=整页藏在屏幕下 1=播放页完全盖住屏幕 2=歌词页。
                            // 注意分母必须是 1：除以 2 会让"播放页全开"仍停在半屏（Sheet 残留感）。
                            translationY = size.height * (1f - offsetFraction.coerceIn(0f, 1f))
                            alpha = (offsetFraction * 8f).coerceIn(0f, 1f)
                        },
                ) {
                    PlayerDetailScreen(
                        state = playback,
                        lyrics = uiState.lyrics,
                        showTranslation = uiState.showTranslation,
                        lyricsOffsetMs = uiState.lyricsOffsetMs,
                        ambientGlow = uiState.ambientGlow,
                        topBarBlurEnabled = uiState.topBarBlurEnabled,
                        topBarBlurMode = when (uiState.topBarBlurMode) {
                            "mask" -> com.wxjxpp.neiro.ui.components.TopBarBlurMode.Mask
                            else -> com.wxjxpp.neiro.ui.components.TopBarBlurMode.Gradient
                        },
                        queue = queue,
                        lyricsAlign = uiState.lyricsAlign,
                        springLyrics = uiState.labSpringLyrics,
                        lyricsFontScale = uiState.lyricsFontScale,
                        lyricsGapScale = uiState.lyricsGapScale,
                        pureModeDefault = uiState.pureModeDefault,
                        sheetProgress = offsetFraction,
                        onCollapseToPlayer = { animateTo(1f) },
                        onExpandLyrics = { animateTo(2f) },
                        onDrag = { deltaPx -> onDragDelta(deltaPx) },
                        onDragEnd = { settleAfterDrag() },
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
                        isFavorite = playback.current?.id in uiState.favoriteSongs.mapTo(mutableSetOf()) { it.id },
                        onToggleFavorite = {
                            playback.current?.let { viewModel.toggleFavorite(it) }
                        },
                        isDownloading = playback.current?.id in uiState.downloadingIds,
                        onDownload = {
                            playback.current?.let { viewModel.downloadSongs(listOf(it)) }
                        },
                    )
                }
            }
        }
    }

    if (showBatchSheet) {
        SongBatchSheet(
            playlists = uiState.playlists,
            songs = batchSongs,
            favoriteIds = uiState.favoriteSongs.mapTo(mutableSetOf()) { it.id },
            onDismiss = { showBatchSheet = false },
            onAddToPlaylist = { playlistId, songs ->
                viewModel.addSongsToPlaylist(playlistId, songs)
            },
            onCreatePlaylistAndAdd = { name, songs ->
                viewModel.createPlaylistWithSongs(name, songs)
            },
            onFavorite = { songs ->
                viewModel.addFavorites(songs)
                showBatchSheet = false
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
            onDownload = { song -> viewModel.downloadSongs(listOf(song)) },
            downloadingIds = uiState.downloadingIds,
            favoriteIds = uiState.favoriteSongs.mapTo(mutableSetOf()) { it.id },
            onToggleFavorite = { song -> viewModel.toggleFavorite(song) },
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
        // 点击切换展开：完整显示多音质档位的失败明细（默认 4 行截断）
        var expanded by remember(message) { mutableStateOf(false) }
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
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = AppTheme.dimens.spaceSm)
                            .clickable { expanded = !expanded },
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
    container: AppContainer,
    viewModel: AppViewModel,
    uiState: ShellUiState,
    hasMediaPermission: Boolean,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigate: (String) -> Unit,
    /** 当前播放歌曲 id（Home 高亮 + 定位）。 */
    currentPlayingId: String? = null,
    onAddSelectedToPlaylist: () -> Unit,
    /** 搜索页等处的批量入口（打开批量弹层）。 */
    onBatchOperate: (List<com.wxjxpp.neiro.core.model.Song>) -> Unit = {},
    /** 轻提示（一起听等页面的状态消息走 Snackbar）。 */
    onToast: (String) -> Unit = {},
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // 页面间切换：淡入淡出（Expressive motionScheme 由主题统一下发节奏）
    // SharedTransitionLayout 提供跨页面共享元素坐标系（专辑 Container Transform 等）
    val rootMotionSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    androidx.compose.animation.SharedTransitionLayout {
        androidx.compose.runtime.CompositionLocalProvider(
            com.wxjxpp.neiro.ui.components.LocalSharedTransitionScope provides this,
        ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                fadeIn(rootMotionSpec) togetherWith fadeOut(rootMotionSpec)
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
                        currentPlayingId = currentPlayingId,
                        sortField = uiState.songSortField,
                        topBarBlurEnabled = uiState.topBarBlurEnabled,
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
                    extraSongs = uiState.favoriteSongs + run {
                        val byId = uiState.songs.associateBy { it.id }
                        uiState.playlists.flatMap { pl -> pl.songIds.mapNotNull { id -> byId[id] } }
                    },
                    favoriteIds = uiState.favoriteSongs.mapTo(mutableSetOf()) { it.id },
                    downloadingIds = uiState.downloadingIds,
                    onDownloadSong = { song -> viewModel.downloadSongs(listOf(song)) },
                    sortField = uiState.albumSortField,
                    sortDescending = uiState.albumSortDescending,
                    onSortFieldChange = viewModel::setAlbumSortField,
                    onSortDirectionToggle = { viewModel.setAlbumSortDescending(!uiState.albumSortDescending) },
                    onOpenDrawer = onOpenDrawer,
                    onSongClick = viewModel::play,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Discover.route -> DiscoverScreen(
                    sections = uiState.discoverSections,
                    isLoading = uiState.isDiscoverLoading,
                    isRefreshing = uiState.isDiscoverLoading && uiState.discoverSections.isNotEmpty(),
                    onRefresh = viewModel::loadDiscover,
                    detailId = uiState.discoverDetailId,
                    detailSongs = uiState.discoverDetailSongs,
                    isDetailLoading = uiState.isDiscoverDetailLoading,
                    toplists = container.discoverRepository.toplists,
                    onOpenDrawer = onOpenDrawer,
                    onSongClick = viewModel::play,
                    onOpenDetail = viewModel::loadDiscoverDetail,
                    onCloseDetail = viewModel::closeDiscoverDetail,
                    onPlayList = viewModel::playDiscoverList,
                    onOpenSearch = { onNavigate(Destination.Search.route) },
                    favoriteIds = uiState.favoriteSongs.mapTo(mutableSetOf()) { it.id },
                    downloadingIds = uiState.downloadingIds,
                    onDownloadSong = { song -> viewModel.downloadSongs(listOf(song)) },
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
                    onFavorites = viewModel::addFavorites,
                    onDownloadMany = viewModel::downloadSongs,
                    onBatchToPlaylist = { songs -> onBatchOperate(songs) },
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Playlists.route -> PlaylistsScreen(
                    playlists = uiState.playlists,
                    songsById = remember(uiState.songs, uiState.favoriteSongs, uiState.playlists) {
                        // 歌单可能包含收藏/其他歌单中的在线歌曲：合并所有已知歌曲再建索引
                        (uiState.songs + uiState.favoriteSongs).associateBy { it.id }
                    },
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
                    onTestHandshake = { api -> container.testUserApiHandshake(api) },
                    onOpenDrawer = onOpenDrawer,
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
                    topBarBlurEnabled = uiState.topBarBlurEnabled,
                    topBarBlurModeStr = uiState.topBarBlurMode,
                    onFloatingPlayerBarChange = viewModel::setFloatingPlayerBar,
                    onShowTranslationChange = viewModel::setShowTranslation,
                    onShuffleModeChange = viewModel::setShuffleMode,
                    onNeteaseCookieChange = viewModel::setNeteaseCookie,
                    onLyricsOffsetChange = viewModel::setLyricsOffset,
                    onPauseOnHeadphoneDisconnectChange = viewModel::setPauseOnHeadphoneDisconnect,
                    onPauseOnAudioFocusLossChange = viewModel::setPauseOnAudioFocusLoss,
                    onAmbientGlowChange = viewModel::setAmbientGlow,
                    onTopBarBlurEnabledChange = viewModel::setTopBarBlur,
                    onTopBarBlurModeChange = viewModel::setTopBarBlurMode,
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
                    downloadDirUri = uiState.downloadDirUri,
                    downloadEmbedCover = uiState.downloadEmbedCover,
                    downloadEmbedLyrics = uiState.downloadEmbedLyrics,
                    onDownloadDirChange = viewModel::setDownloadDir,
                    onDownloadEmbedCoverChange = viewModel::setDownloadEmbedCover,
                    onDownloadEmbedLyricsChange = viewModel::setDownloadEmbedLyrics,
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

                Destination.Favorites.route -> FavoritesScreen(
                    songs = uiState.favoriteSongs,
                    downloadingIds = uiState.downloadingIds,
                    onOpenDrawer = onOpenDrawer,
                    onSongClick = viewModel::play,
                    onRemoveFavorite = viewModel::toggleFavorite,
                    onDownloadSong = { song -> viewModel.downloadSongs(listOf(song)) },
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                )

                Destination.Together.route -> TogetherScreen(
                    transport = container.togetherTransport as LitTogetherTransport,
                    player = container.playerController,
                    onMessage = onToast,
                    search = container.onlineSearch,
                    resolveUrl = { song ->
                        val r = container.resolveRemoteUrl(song)
                        (r as? com.wxjxpp.neiro.core.player.Media3PlayerController.RemoteUrl.Success)?.url
                    },
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
    }
}

private fun inSelectionModeCompat(uiState: ShellUiState): Boolean = uiState.selectedSongIds.isNotEmpty()