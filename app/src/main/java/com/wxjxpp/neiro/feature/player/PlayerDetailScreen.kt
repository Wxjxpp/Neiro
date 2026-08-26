package com.wxjxpp.neiro.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs
import com.wxjxpp.neiro.core.model.Lyrics
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.PlaybackState
import com.wxjxpp.neiro.core.model.Quality
import com.wxjxpp.neiro.core.model.RepeatMode
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.components.AmbientGlowBackground
import com.wxjxpp.neiro.ui.components.TopBarBlurMode
import com.wxjxpp.neiro.ui.components.topBarBlur
import com.wxjxpp.neiro.ui.components.SongCover
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 播放详情页（Sheet 形态）。
 *
 * 由连续的 [sheetProgress] 驱动（0=收起 1=播放页 2=歌词页）：
 * - 封面 ↔ 歌词的过渡不是页面切换，而是同一进度上的**连续变换**：
 *   进度 1→1.5 封面缩小上移淡出，1.5→2 歌词从下方滑入聚焦；
 *   反向拖拽完全对称。手指停在哪，画面就停在哪（跟手）。
 * - 封面定位：顶部约 25% 屏高，尺寸比常规放大 ~10%（用户指定比例）
 * - 歌词聚焦位：视口 33% 高度处（由 LyricsPane 内部处理）
 * - 手势层级：顶部区域下滑 = 整体收起；封面/标题上滑 = 进入歌词页；
 *   歌词页仅在小封面上做手势（不与歌词滚动冲突），返回按钮逐层回退。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun PlayerDetailScreen(
    state: PlaybackState,
    lyrics: Lyrics,
    showTranslation: Boolean,
    lyricsOffsetMs: Long,
    ambientGlow: Boolean,
    /** 顶栏模糊总开关（设置页可控，默认关）。 */
    topBarBlurEnabled: Boolean = false,
    /** 顶栏模糊模式：渐变模糊 / 遮罩模糊。 */
    topBarBlurMode: TopBarBlurMode = TopBarBlurMode.Gradient,
    queue: List<Song>,
    lyricsAlign: String = "center",
    springLyrics: Boolean = false,
    lyricsFontScale: Float = 1f,
    lyricsGapScale: Float = 1f,
    pureModeDefault: Boolean = false,
    /** Sheet 连续进度：0 收起 / 1 播放页 / 2 歌词页。 */
    sheetProgress: Float = 1f,
    onCollapseToPlayer: () -> Unit = {},
    onExpandLyrics: () -> Unit = {},
    /** 垂直拖拽增量回调（px，向上为负）：交给外壳换算进度。 */
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPickQueueItem: (Int) -> Unit,
    onLyricsOffsetChange: (Long) -> Unit,
    onMatchLyrics: () -> Unit = {},
    onToggleTranslation: () -> Unit = {},
    onSpeedChange: (Float) -> Unit = {},
    currentQuality: Quality = Quality.Standard,
    onQualityChange: (Quality) -> Unit = {},
    /** 收藏/下载能力由外壳注入；null = 隐藏对应按钮。 */
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    isDownloading: Boolean = false,
    onDownload: (() -> Unit)? = null,
) {
    val song = state.current ?: return
    val dimens = AppTheme.dimens
    var dragging by remember { mutableStateOf(false) }
    // 翻译显示开关：默认开启，可在播放页直接切换（同时通知设置持久化）
    var translationOn by remember(showTranslation) { mutableStateOf(showTranslation) }
    var pureModeOverride by remember { mutableStateOf<Boolean?>(null) }
    val pureMode = pureModeOverride ?: pureModeDefault
    var showQueue by remember { mutableStateOf(false) }
    var showOffsetPanel by remember { mutableStateOf(false) }
    // 主区域顶部相对根 Box 的 Y 偏移（含状态栏+标题高度），供封面矩形插值定位
    var mainAreaTopPx by remember { mutableFloatStateOf(0f) }
    val isRemoteSong = song.location is MediaLocation.Remote
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

    // 歌词段(1..2) 归一化进度，用于连续变换
    val lyricPhase = ((sheetProgress - 1f) / 1f).coerceIn(0f, 1f)
    // 歌词模式判定阈值（内容切换在过半时发生，避免中途闪烁）
    val lyricsMode = lyricPhase > 0.5f

    // ---- 沉浸式配色：真实封面取色 → 全屏深色画布（参考 Salt Player，与主题明暗无关）----
    val context = androidx.compose.ui.platform.LocalContext.current
    val coverBitmapForPalette by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = song.coverUri,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val uri = song.coverUri?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 24 }
                when {
                    uri.startsWith("content://") ->
                        context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                            ?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                    // 网络歌曲封面走 Coil（原实现只支持本地路径，在线歌加载失败）
                    uri.startsWith("http") -> {
                        val req = coil.request.ImageRequest.Builder(context)
                            .data(uri)
                            .allowHardware(false)
                            .build()
                        (coil.Coil.imageLoader(context).execute(req) as? coil.request.SuccessResult)
                            ?.let { it.drawable as? android.graphics.drawable.BitmapDrawable }
                            ?.bitmap
                    }
                    else -> android.graphics.BitmapFactory.decodeFile(uri, opts)
                }
            }.getOrNull()
        }
    }
    // 从缩略图提取主色混入深色画布；取色未就绪时先用中性深底，不闪白
    val immersiveScheme = remember(coverBitmapForPalette, song.id) {
        var dark = darkColorScheme(
            primary = Color(song.coverSeedColor),
            onPrimary = Color.White,
            background = Color(0xFF101014),
            onBackground = Color.White.copy(alpha = 0.92f),
            surface = Color(0xFF16161B),
            onSurface = Color.White.copy(alpha = 0.90f),
            onSurfaceVariant = Color.White.copy(alpha = 0.62f),
            surfaceVariant = Color.White.copy(alpha = 0.08f),
            outline = Color.White.copy(alpha = 0.35f),
        )
        coverBitmapForPalette?.let { bmp ->
            val c = bmp.extractDominantArgb().takeIf { it != 0 } ?: return@let
            dark = dark.copy(
                primary = Color(c),
                background = Color(
                    androidx.core.graphics.ColorUtils.blendARGB(
                        Color.Black.toArgb(), c, 0.38f,
                    ),
                ),
                surface = Color(
                    androidx.core.graphics.ColorUtils.blendARGB(
                        Color.Black.toArgb(), c, 0.24f,
                    ),
                ),
            )
        }
        dark
    }
    androidx.compose.material3.MaterialTheme(colorScheme = immersiveScheme) {
    // Expr：Haze 硬件加速模糊源与开关（顶栏毛玻璃实验；Haze 内部自带低版本回退）
    val topBarHaze = rememberHazeState()
    val useTopBarHaze = topBarBlurEnabled
    Box(modifier = Modifier.fillMaxSize()) {
        // 背景：流光开启时是动态光斑，关闭时也必须有 surface 实底（绝不能透明露出底层页面）
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        AmbientGlowBackground(
            baseColor = Color(song.coverSeedColor),
            coverUri = song.coverUri,
            enabled = ambientGlow,
            modifier = Modifier
                .fillMaxSize()
                // Expr：标记为 Haze 模糊源（顶栏实时采样流光画面）
                .hazeSource(topBarHaze)
                // 进入歌词页时随进度平滑淡出，避免中途突然消失
                .alpha((1f - ((lyricPhase - 0.3f) / 0.4f)).coerceIn(0f, 1f)),
        )
        // 顶部安全区实心填充 + 下滑整体收起的手势区（可选毛玻璃：先模糊再遮罩）
        Spacer(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusBarPadding.calculateTopPadding() + 88.dp)
                .then(
                    // Expr：Haze 硬件加速模糊优先；关闭时走原 RenderEffect 路径
                    if (useTopBarHaze) {
                        Modifier.hazeBlur(
                            input = HazeInput.Sources(topBarHaze),
                            style = HazeBlurStyle {
                                blurRadius(22.dp)
                                backgroundColor(MaterialTheme.colorScheme.surface.copy(alpha = 0.18f))
                            },
                        )
                    } else {
                        Modifier.topBarBlur(enabled = topBarBlurEnabled, mode = topBarBlurMode)
                            .background(
                                Brush.verticalGradient(
                                    0f to MaterialTheme.colorScheme.surface,
                                    0.82f to MaterialTheme.colorScheme.surface,
                                    1f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                ),
                            )
                    },
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    ) { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0f) onDrag(dragAmount) // 下滑收起
                    }
                },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarPadding.calculateTopPadding()),
        ) {
            Spacer(Modifier.height(dimens.spaceSm))
            // 标题区：播放页大标题 ↔ 歌词页小封面行，同一进度上的连续交叉过渡
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 播放页大标题（居中，随 lyricPhase 缩小淡出）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spaceXl, vertical = dimens.spaceSm)
                        .graphicsLayer {
                            alpha = 1f - (lyricPhase * 2.2f).coerceIn(0f, 1f)
                            val sc = 1f - 0.12f * lyricPhase
                            scaleX = sc; scaleY = sc
                            cameraDistance = 8f * density
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.marqueeIfLong(),
                    )
                }
                // 歌词页头部行：小封面 + 标题歌手（随 lyricPhase 淡入上滑）
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = ((lyricPhase - 0.55f) * 3f).coerceIn(0f, 1f)
                            translationY = (1f - ((lyricPhase - 0.55f) * 3f).coerceIn(0f, 1f)) * 14.dp.toPx()
                        }
                        .padding(start = 12.dp, end = dimens.spaceXl, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 占位：根层变形封面最终精确落位在这里（单一实例渲染，无重复）
                    Spacer(modifier = Modifier.size(52.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artistName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // 主区域：歌词层（随进度滑入）+ 全域手势；大封面上浮到根层做矩形插值
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .onGloballyPositioned { mainAreaTopPx = it.positionInParent().y }
                    // 整个主区域上滑/下滑：全量转发像素位移，跟手由外壳 snapTo 保证
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                        ) { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        }
                    },
            ) {
                val t = lyricPhase.coerceIn(0f, 1f)
                // 歌词层：整层从下方滑入，过半后可交互
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationY = (1f - ((t - 0.25f) / 0.75f).coerceIn(0f, 1f)) * size.height
                            alpha = ((t - 0.15f) / 0.35f).coerceIn(0f, 1f)
                        },
                ) {
                    if (lyrics.isEmpty) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "没有找到歌词",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            androidx.compose.material3.TextButton(onClick = onMatchLyrics) {
                                Text("从网络匹配歌词")
                            }
                        }
                    } else {
                        // Expr 实验：Accompanist 歌词渲染（黏滞弹簧行切换动画）
                        AccompanistLyricsPane(
                            lyrics = lyrics,
                            positionMs = state.positionMs,
                            title = song.title,
                            artistName = song.artistName,
                            showTranslation = translationOn,
                            offsetMs = lyricsOffsetMs,
                            fontScale = lyricsFontScale,
                            onSeekTo = onSeekTo,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        // 变形封面（根层）：单一实例在「播放页大图」与「歌词页头部小图」之间做矩形插值。
        // 纯位移 + 尺寸变化：无淡入淡出、无实例切换；拖拽全量转发保证跟手，点击进入歌词页。
        run {
            val tMorph = lyricPhase.coerceIn(0f, 1f)
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val screenWpx = with(density) { configuration.screenWidthDp.dp.toPx() }
            val screenHpx = with(density) { configuration.screenHeightDp.dp.toPx() }
            val statusBarPx = with(density) { statusBarPadding.calculateTopPadding().toPx() }
            val spaceSmPx = with(density) { dimens.spaceSm.toPx() }
            val coverTarget = dimens.detailCoverSize * 1.10f
            val bigSizePx = with(density) { coverTarget.toPx() }
            val smallLeftPx = with(density) { 12.dp.toPx() }
            val bigLeft = (screenWpx - bigSizePx) / 2f
            val bigTop = mainAreaTopPx + screenHpx * 0.25f - statusBarPx - spaceSmPx
            val smallTop = statusBarPx + spaceSmPx + with(density) { 4.dp.toPx() }
            SongCover(
                song = song,
                size = with(density) { androidx.compose.ui.unit.lerp(coverTarget, 52.dp, tMorph) },
                radius = with(density) {
                    androidx.compose.ui.unit.lerp(dimens.detailCoverRadius, 9.dp, tMorph)
                },
                modifier = Modifier
                    .offset(
                        x = with(density) { (bigLeft + (smallLeftPx - bigLeft) * tMorph).toDp() },
                        y = with(density) { (bigTop + (smallTop - bigTop) * tMorph).toDp() },
                    )
                    // 注意：不加 shadowElevation——graphicsLayer 阴影是矩形轮廓，
                    // 不跟随圆角，会在封面背后露出方形阴影边角
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                        ) { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onExpandLyrics() })
                    },
            )
        }
        // ---- 控制台 ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        0.12f to MaterialTheme.colorScheme.surface,
                        1f to MaterialTheme.colorScheme.surface,
                    ),
                ),
        ) {
            // 当前歌词横幅：仅播放页显示（歌词页有完整 LyricsPane，避免重复）
            if (!lyricsMode && !lyrics.isEmpty) {
                CurrentLineBanner(
                    lyrics = lyrics,
                    positionMs = state.positionMs,
                    offsetMs = lyricsOffsetMs,
                    showTranslation = showTranslation,
                    onClick = onExpandLyrics,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            var draggingProgress by remember(song.id) { mutableFloatStateOf(state.progress) }
            val shownProgress = if (dragging) draggingProgress else state.progress
            WavySeekBar(
                progress = shownProgress,
                animated = state.isPlaying && !dragging,
                onValueChange = {
                    draggingProgress = it
                    dragging = true
                },
                onValueChangeFinished = {
                    onSeekFraction(draggingProgress)
                    dragging = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceXl),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceXl),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDuration(if (dragging) (state.durationMs * shownProgress).toLong() else state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 播放控制行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceXl, vertical = dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (!pureMode) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onCycleRepeat() })
                        },
                    ) {
                        Icon(
                            imageVector = if (state.shuffle) Icons.Rounded.Shuffle else when (state.repeatMode) {
                                RepeatMode.One -> Icons.Rounded.RepeatOne
                                RepeatMode.All -> Icons.Rounded.Repeat
                                RepeatMode.Off -> Icons.Rounded.Shuffle
                            },
                            contentDescription = "随机/循环（长按切循环）",
                            tint = if (state.shuffle || state.repeatMode != RepeatMode.Off) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首")
                    }
                    IconButton(
                        onClick = {
                            if (pureMode && state.isPlaying) pureModeOverride = false
                            onTogglePlay()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { pureModeOverride = true })
                            },
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停（长按进入纯净模式）" else "播放",
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
                    }
                }
                if (!pureMode) {
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "播放列表")
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
            // 功能区：收藏 / 歌词切换 / 更多菜单（下载、歌词偏移、翻译、倍速、音质并入菜单）
            var showMoreMenu by remember { mutableStateOf(false) }
            if (!pureMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onToggleFavorite != null) {
                        val fav = isFavorite
                        IconButton(onClick = { onToggleFavorite() }) {
                            Icon(
                                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (fav) "取消收藏" else "收藏",
                                tint = if (fav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (lyricsMode) onCollapseToPlayer() else onExpandLyrics()
                    }) {
                        Icon(
                            Icons.Rounded.Lyrics,
                            contentDescription = "显示歌词",
                            tint = if (lyricsMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    // 更多菜单：下载 / 歌词偏移 / 翻译 / 倍速 / 音质
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            if (isRemoteSong && onDownload != null) {
                                DropdownMenuItem(
                                    text = { Text(if (isDownloading) "正在下载…" else "下载歌曲") },
                                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                    enabled = !isDownloading,
                                    onClick = {
                                        showMoreMenu = false
                                        onDownload?.invoke()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(if (lyricsOffsetMs == 0L) "歌词偏移" else "歌词偏移 (${lyricsOffsetMs}ms)")
                                },
                                leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showOffsetPanel = !showOffsetPanel
                                },
                            )
                            if (lyrics.hasTranslation) {
                                DropdownMenuItem(
                                    text = { Text("歌词翻译") },
                                    leadingIcon = { Icon(Icons.Rounded.Translate, contentDescription = null) },
                                    trailingIcon = {
                                        if (translationOn) Icon(Icons.Rounded.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        translationOn = !translationOn
                                        onToggleTranslation()
                                    },
                                )
                            }
                            listOf(1f, 1.25f, 1.5f, 2f).forEachIndexed { idx, sp ->
                                val label = listOf("1x 正常", "1.25x", "1.5x", "2x")[idx]
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    trailingIcon = {
                                        if (abs(state.speed - sp) < 0.01f) {
                                            Icon(Icons.Rounded.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onSpeedChange(sp)
                                    },
                                )
                            }
                            if (isRemoteSong) {
                                listOf(
                                    Quality.Low,
                                    Quality.Standard,
                                    Quality.High,
                                    Quality.Lossless,
                                    Quality.HiRes,
                                ).forEach { q ->
                                    DropdownMenuItem(
                                        text = { Text("音质 · " + qualityLabel(q)) },
                                        trailingIcon = {
                                            if (currentQuality == q) {
                                                Icon(Icons.Rounded.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            if (q != currentQuality) onQualityChange(q)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // 歌词偏移调节面板
        if (showOffsetPanel && lyricsMode && !pureMode) {
            LyricsOffsetPanel(
                offsetMs = lyricsOffsetMs,
                onChange = onLyricsOffsetChange,
                onDismiss = { showOffsetPanel = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            )
        }
        // 返回按钮：已移除（返回手势/下滑即可收起）
    }
    if (showQueue && !pureMode) {
        QueueSheet(
            queue = queue,
            currentSongId = song.id,
            onDismiss = { showQueue = false },
            onPick = { index ->
                onPickQueueItem(index)
                showQueue = false
            },
        )
    }
    }
}
/** 音质档位的短标签。 */
internal fun qualityLabel(q: Quality): String = when (q) {
    Quality.Low -> "低"
    Quality.Standard -> "标"
    Quality.High -> "高"
    Quality.Lossless -> "无"
    Quality.HiRes -> "Hi"
}

/** 封面模式下的当前歌词横幅：5 行窗口（前2 + 当前 + 后2）。 */
@Composable
private fun CurrentLineBanner(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Long,
    showTranslation: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveOffset = offsetMs + lyrics.offsetMs
    val p = positionMs - effectiveOffset
    var index = -1
    for (i in lyrics.lines.indices) {
        if (lyrics.lines[i].startMs <= p) index = i else break
    }
    if (index < 0) return
    val lines = lyrics.lines
    // 5 行窗口：index-2 .. index+2，越界跳过
    val window = (index - 2)..(index + 2)
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                // Offscreen 必须在 drawWithContent 之前：先隔离出离屏缓冲，
                // DstIn 渐变才只作用于横幅自身内容（否则会擦穿背景露出黑边）
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    // 上/下边缘柔和淡出（DstIn：alpha=1 保留，alpha=0 擦除）
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0f),
                            0.25f to Color.Black.copy(alpha = 1f),
                            0.75f to Color.Black.copy(alpha = 1f),
                            1f to Color.Black.copy(alpha = 0f),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            for (i in window) {
                if (i < 0 || i >= lines.size) continue
                val line = lines[i]
                val isCurrent = i == index
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * if (isCurrent) 1.15f else 0.92f,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(vertical = 1.dp)
                        .alpha(if (isCurrent) 1f else 0.75f - (abs(i - index) - 1).coerceAtMost(2) * 0.15f),
                )
                if (isCurrent && showTranslation && !line.translation.isNullOrBlank()) {
                    Text(
                        text = line.translation!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
/** 从缩略图粗提主色：跳过近黑/近白像素，按饱和度加权平均（Salt Player 风格沉浸底色）。 */
private fun android.graphics.Bitmap.extractDominantArgb(): Int {
    val step = maxOf(1, width / 32)
    var r = 0L
    var g = 0L
    var b = 0L
    var wsum = 0L
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val px = getPixel(x, y)
            val pr = (px shr 16) and 0xFF
            val pg = (px shr 8) and 0xFF
            val pb = px and 0xFF
            val mx = maxOf(pr, pg, pb)
            val sat = mx - minOf(pr, pg, pb)
            if (mx > 28 && mx < 236) {
                val w = (sat * sat + 64).toLong()
                r += pr * w
                g += pg * w
                b += pb * w
                wsum += w
            }
            x += step
        }
        y += step
    }
    if (wsum == 0L) return 0
    return android.graphics.Color.rgb(
        (r / wsum).toInt(),
        (g / wsum).toInt(),
        (b / wsum).toInt(),
    )
}
/** 歌词偏移调节面板：紧凑版，±50ms。 */
@Composable
private fun LyricsOffsetPanel(
    offsetMs: Long,
    onChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier.padding(horizontal = 56.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("偏移", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "%+d ms".format(offsetMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = offsetMs.toFloat().coerceIn(-50f, 50f),
                onValueChange = { onChange(it.toLong()) },
                valueRange = -5000f..5000f,
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("-50", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.TextButton(onClick = { onChange(0L) }) {
                    Text("重置", style = MaterialTheme.typography.labelMedium)
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text("完成", style = MaterialTheme.typography.labelMedium)
                }
                Text("+50", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 文本超长时启用跑马灯滚动。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.marqueeIfLong(): Modifier =
    this.then(Modifier.basicMarquee(iterations = Int.MAX_VALUE))

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}