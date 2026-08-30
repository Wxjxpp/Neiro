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
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface

import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material3.Switch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.delay
import com.wxjxpp.neiro.core.model.Lyrics
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.PlaybackState
import com.wxjxpp.neiro.core.model.Quality
import com.wxjxpp.neiro.core.model.RepeatMode
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.components.AlbumBlurBackground
import com.wxjxpp.neiro.ui.components.FluidGlowBackground
import com.wxjxpp.neiro.ui.components.TopBarBlurMode
import com.wxjxpp.neiro.ui.components.topBarBlur
import com.wxjxpp.neiro.ui.components.SongCover
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 长按播放键切换纯净模式所需的按住时长。
 *
 * 用户指定 2.5s：明显长于系统默认长按阈值（约 500ms），
 * 不会被"手指停顿一下"误触发。
 */
private const val PURE_MODE_LONG_PRESS_MS = 2500L

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
    /** 播放页视觉风格由系统深浅主题决定：暗色动态流体，浅色鲜艳按钮。 */
    topBarBlurEnabled: Boolean = true,
    /** 顶栏模糊模式：渐变模糊 / 遮罩模糊。 */
    topBarBlurMode: TopBarBlurMode = TopBarBlurMode.Gradient,
    queue: List<Song>,
    lyricsAlign: String = "center",
    springLyrics: Boolean = false,
    lyricsFontScale: Float = 1f,
    lyricsGapScale: Float = 1f,
    pureModeDefault: Boolean = false,
    /** 纯净模式开关持久化回调（长按播放键切换时调用，写入设置）。 */
    onPureModeChange: (Boolean) -> Unit = {},
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
    eqEnabled: Boolean = false,
    eqGains: FloatArray = FloatArray(10),
    eqCustomPresetsJson: String = "[]",
    onToggleEqualizer: (Boolean) -> Unit = {},
    onEqGainsChange: (FloatArray) -> Unit = {},
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
    // 纯净模式：**唯一真源是持久化设置**（pureModeDefault）。
    //
    // v8 之前这里有一个本地 `pureModeOverride: Boolean?`，导致两个 bug：
    //   1) 重进播放页 / 进程重启就丢失 —— 用户说的"不持久"；
    //   2) 播放键的 onClick 里写了 `if (pureMode && isPlaying) override = false`，
    //      所以点两下播放键就退出了纯净模式。
    // 现在长按 2.5s 直接把新值写进设置，UI 靠设置回流刷新，不再有第二份状态。
    val pureMode = pureModeDefault
    var showQueue by remember { mutableStateOf(false) }
    // Expr：更多操作 Sheet 开关（根部作用域，供底部 ModalBottomSheet 使用）
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAudioFxSheet by remember { mutableStateOf(false) }
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
    // Expr：主题自适应——浅色模式提鲜亮（提高主色保留量），暗色模式更深沉
    // Expr2：前景黑/白按**采样后的实际背景亮度**自适应——白色专辑封面采样出
    // 亮背景时文字/图标自动转黑，深色封面保持白字（用户指定：纯白底黑字/纯黑底白字）
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // 视觉风格不再提供手动开关：暗色主题固定使用动态流体取色，
    // 浅色主题固定使用鲜艳大按钮。这样主题切换后立即得到对应的默认体验。
    val vividMode = !isDarkTheme
    // Expr v2：多点取色板（v5 性能：移到后台线程，且全页只算一次）
    //
    // 原实现有两处问题：① `remember { bmp.extractVividPalette() }` 在**主线程组合期**
    // 跑 6 组窗口采样（每组约 80 次 getPixel，getPixel 是 JNI 调用），换歌瞬间直接
    // 占用 UI 线程；② immersiveScheme 里又独立调了一次 extractVividPalette，
    // 同一张图算两遍。现在统一在 Default 调度器算一次，两处共用。
    val vividPalette by androidx.compose.runtime.produceState(
        initialValue = emptyList<Color>(),
        key1 = coverBitmapForPalette,
    ) {
        val bmp = coverBitmapForPalette
        value = if (bmp == null) {
            emptyList()
        } else {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { bmp.extractVividPalette().map { Color(it) } }.getOrDefault(emptyList())
            }
        }
    }
    val immersiveScheme = remember(vividPalette, song.id, isDarkTheme, vividMode) {
        var dark = darkColorScheme(
            // Expr vivid 风格：高饱和明亮主色（调色板就绪后二次覆盖）
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
        vividPalette.takeIf { it.isNotEmpty() }?.let { pal ->
            // 复用上面后台算好的调色板，不再重复采样
            val palette = pal.map { it.toArgb() }
            val c = palette.firstOrNull() ?: return@let
            // 关键修正：不再无条件混黑。
            // 采样色偏亮（白/浅色封面）→ 画布走浅色系 + 黑色前景；
            // 采样色偏暗 → 画布混黑走深色系 + 白色前景。
            // 这样"纯白封面得到白底黑字、深色封面得到深底白字"，与用户要求一致。
            // vivid 模式：强制明亮鲜艳画布，主色用最高饱和明亮色，前景深色
            if (vividMode) {
                val vividPrimary = palette.maxByOrNull { p ->
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(p, hsv)
                    hsv[1] * (0.4f + hsv[2])
                } ?: c
                dark = dark.copy(
                    primary = Color(vividPrimary),
                    onPrimary = Color(0xFF14141A),
                    background = Color(
                        androidx.core.graphics.ColorUtils.blendARGB(vividPrimary, Color.White.toArgb(), 0.68f),
                    ),
                    surface = Color(
                        androidx.core.graphics.ColorUtils.blendARGB(palette.last(), Color.White.toArgb(), 0.78f),
                    ),
                    onBackground = Color(0xFF14141A).copy(alpha = 0.95f),
                    onSurface = Color(0xFF14141A).copy(alpha = 0.93f),
                    onSurfaceVariant = Color(0xFF14141A).copy(alpha = 0.62f),
                    surfaceVariant = Color(0xFF14141A).copy(alpha = 0.07f),
                    outline = Color(0xFF14141A).copy(alpha = 0.35f),
                )
                return@let
            }
            val coverLum = androidx.core.graphics.ColorUtils.calculateLuminance(c)
            // v6 修复：画布明暗必须**先服从系统深浅色模式**。
            // 旧规则只看封面明度（coverLum > 0.42 就走浅底），于是深色模式下
            // 播白色专辑会得到一整屏亮米黄/亮粉 —— 用户报的正是这个。
            // 现在深色模式一律走深底，只有浅色模式才允许浅底。
            val lightCanvas = !isDarkTheme && coverLum > 0.42
            val bgArgb: Int
            val sfArgb: Int
            if (lightCanvas) {
                // 浅色画布：向白提亮（保留封面色相，避免刺眼纯白）
                bgArgb = androidx.core.graphics.ColorUtils.blendARGB(c, Color.White.toArgb(), 0.55f)
                sfArgb = androidx.core.graphics.ColorUtils.blendARGB(c, Color.White.toArgb(), 0.38f)
            } else {
                // 深色画布：混黑沉浸（浅色主题保留主色多一点更鲜亮）
                val bgMix = if (isDarkTheme) 0.34f else 0.52f
                val sfMix = if (isDarkTheme) 0.22f else 0.40f
                bgArgb = androidx.core.graphics.ColorUtils.blendARGB(Color.Black.toArgb(), c, bgMix)
                sfArgb = androidx.core.graphics.ColorUtils.blendARGB(Color.Black.toArgb(), c, sfMix)
            }
            // 前景由画布明度决定（不是由 App 主题决定）
            val fg = if (androidx.core.graphics.ColorUtils.calculateLuminance(bgArgb) > 0.35) {
                Color(0xFF14141A)
            } else {
                Color.White
            }
            dark = dark.copy(
                primary = Color(c),
                background = Color(bgArgb),
                surface = Color(sfArgb),
                onBackground = fg.copy(alpha = 0.94f),
                onSurface = fg.copy(alpha = 0.92f),
                onSurfaceVariant = fg.copy(alpha = 0.66f),
                surfaceVariant = fg.copy(alpha = 0.10f),
                outline = fg.copy(alpha = 0.38f),
                onPrimary = if (coverLum > 0.42) Color(0xFF14141A) else Color.White,
            )
        }
        dark
    }
    // Expr v3: accent = palette 中与画布亮度差最大且饱和度最高的颜色, 按钮开关滑杆统一取色
    val canvasLum = androidx.core.graphics.ColorUtils.calculateLuminance(immersiveScheme.background.toArgb())
    val accentColor = remember(vividPalette, song.id, isDarkTheme, canvasLum) {
        fun lum(argb: Int) = androidx.core.graphics.ColorUtils.calculateLuminance(argb)
        data class Cand(val hsv: FloatArray, val score: Float)
        val best: Cand? = vividPalette.mapNotNull { col ->
            val argb = col.toArgb()
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
            if (hsv[1] < 0.18f) return@mapNotNull null
            val contrast = kotlin.math.abs((lum(argb) - canvasLum).toDouble()).toFloat()
            Cand(hsv, (contrast * (0.35f + hsv[1])).toFloat())
        }.maxByOrNull { it.score }
        if (best == null) {
            // v6：整张封面都是中性色（白/灰/黑专辑）。此时**绝不能**凭空造一个
            // 彩色 accent —— 那正是"白封面出粉红"的观感来源。改用中性高对比：
            // 深画布 → 近白，浅画布 → 近黑。视觉上干净，且对比度天然达标。
            if (canvasLum < 0.5f) Color(0xFFE8EAF0) else Color(0xFF2A2C33)
        } else {
            val hsv = floatArrayOf(best.hsv[0], best.hsv[1], best.hsv[2])
            if (canvasLum < 0.5f && hsv[2] < 0.72f) hsv[2] = 0.72f
            if (canvasLum >= 0.5f && hsv[2] > 0.55f) hsv[2] = 0.55f
            Color(android.graphics.Color.HSVToColor(hsv))
        }
    }
    val finalScheme = remember(immersiveScheme, accentColor) {
        val onAcc = if (androidx.core.graphics.ColorUtils.calculateLuminance(accentColor.toArgb()) > 0.4f) {
            Color(0xFF14141A)
        } else Color.White
        immersiveScheme.copy(
            primary = accentColor,
            onPrimary = onAcc,
            secondaryContainer = accentColor.copy(alpha = 0.16f),
            onSecondaryContainer = accentColor,
            tertiary = accentColor,
        )
    }
    androidx.compose.material3.MaterialTheme(colorScheme = finalScheme) {
    // Expr：Haze 硬件加速模糊源与开关（顶栏毛玻璃实验；Haze 内部自带低版本回退）
    val topBarHaze = rememberHazeState()
    val useTopBarHaze = topBarBlurEnabled
    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层必须和播放页一起位移，不能用 sheetProgress 作为“从 0 渐入”的透明度。
        // 否则下滑收起的前 30% 只露出下面页面，随后才突然出现专辑背景。
        // 动态流体仅在用户明确开启且为暗色主题时启用；默认关闭时保留静态专辑模糊。
        if (!vividMode && ambientGlow && vividPalette.isNotEmpty()) {
            FluidGlowBackground(
                palette = vividPalette,
                enabled = true,
                canvasColor = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(topBarHaze)
                    .alpha((1f - ((lyricPhase - 0.3f) / 0.4f)).coerceIn(0f, 1f)),
            )
        } else if (sheetProgress > 0.01f) {
            // 不再使用 sheetProgress/0.7f：背景在手指拖拽期间保持同一层，
            // 由外层 MusicPlayerApp 的 translationY 统一跟手移动。
            AlbumBlurBackground(
                coverUri = song.coverUri,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(topBarHaze),
            )
        }
        // 顶部安全区：上方保持实色；只有最底部 40dp 作为渐变模糊过渡。
        // 不再把整块顶栏做成半透明色块，避免搜索胶囊/标题上方出现“一坨盖板”。
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusBarPadding.calculateTopPadding() + 88.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    ) { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0f) onDrag(dragAmount) // 下滑收起
                    }
                },
        ) {
            // 顶部主体保持实色；末尾 40dp 从近实色向透明过渡，避免底边硬切。
            Spacer(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height((statusBarPadding.calculateTopPadding() + 88.dp - 40.dp).coerceAtLeast(0.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
            if (useTopBarHaze) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(72.dp)
                        .hazeEffect(
                            state = topBarHaze,
                            style = HazeStyle(
                                backgroundColor = Color.Transparent,
                                tints = listOf(HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.06f))),
                                blurRadius = 22.dp,
                            ),
                        )
                        // 上端接近实色，下端逐渐透明；背景内容在末尾区域自然显现。
                        .background(
                            Brush.verticalGradient(
                                0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                0.45f to MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                                1f to Color.Transparent,
                            ),
                        )
                        // 过渡层向内容区域延伸 32dp，避免毛玻璃 RenderNode 在顶栏边界处被裁成硬线。
                        .graphicsLayer {
                            translationY = 32.dp.toPx()
                        },
                )
            }
        }
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
                // 收起态不组合歌词渲染树，避免屏幕外的动画和布局继续消耗 CPU。
                if (sheetProgress > 0.5f) {
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
                            isPlaying = state.isPlaying,
                            showTranslation = translationOn,
                            offsetMs = lyricsOffsetMs,
                            fontScale = lyricsFontScale,
                            gapScale = lyricsGapScale,
                            onSeekTo = onSeekTo,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
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
                // 详情页大图走独立全清缓存键：歌词页往返不再命中低清缓存
                fullQuality = true,
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
                        detectTapGestures(onTap = {
                            // 歌词页点封面 → 回播放页；播放页点封面 → 进歌词页
                            if (lyricsMode) onCollapseToPlayer() else onExpandLyrics()
                        })
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
                        0.12f to MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                        1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
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
            // 播放控制行（Material 3 Expressive 规范）
            //
            // 布局：三段式 weight —— 左辅助键 / 中间主控组 / 右辅助键。
            // 左右各占 weight(1f) 分别贴边，中间组 wrapContent，因此中间三键
            // 永远处于屏幕几何正中（此前 SpaceBetween 会因左右宽度不等而偏移）。
            //
            // 按钮：统一用官方 FilledIconButton + IconButtonDefaults 尺寸令牌，
            // 不再手写 Box + background。形状/按压变形/配色交给组件库，
            // 后期维护只需替换尺寸令牌（extraLarge / large / medium / small）。
            // 当前用 medium：large 在常见机型上会占满整行，把左右辅助键挤出可视区。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧：随机 / 循环
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (!pureMode) {
                        IconButton(
                            onClick = onToggleShuffle,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onToggleShuffle() },
                                    onLongPress = { onCycleRepeat() },
                                )
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
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                },
                            )
                        }
                    }
                }
                // 中间：上一首 / 播放 / 下一首（几何居中）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                ) {
                    val vivid = !isDarkTheme
                    // 侧键容器色与画布对比（深画布→浅键 / 浅画布→深键）；主键 accent 实底。
                    val sideColors = if (vivid) {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (canvasLum < 0.5f) Color(0xFFE9ECF2) else Color(0xFF2A2C33),
                            contentColor = if (canvasLum < 0.5f) Color(0xFF1A1C22) else Color(0xFFF2F3F7),
                        )
                    } else {
                        IconButtonDefaults.filledTonalIconButtonColors()
                    }
                    // 上一首：侧键用 medium 容器 + Uniform 宽度
                    FilledIconButton(
                        onClick = onPrevious,
                        shapes = IconButtonDefaults.shapes(
                            shape = IconButtonDefaults.mediumRoundShape,
                            pressedShape = IconButtonDefaults.mediumPressedShape,
                        ),
                        modifier = Modifier.size(
                            IconButtonDefaults.mediumContainerSize(
                                IconButtonDefaults.IconButtonWidthOption.Uniform,
                            ),
                        ),
                        colors = sideColors,
                    ) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "上一首",
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                        )
                    }
                    // 播放/暂停：主键，medium 容器 + Wide 宽度（比侧键更宽，主次分明）
                    //
                    // 纯净模式长按（v8 重写）：
                    // 旧实现在 Modifier 上挂 pointerInput(detectTapGestures)，而 FilledIconButton
                    // 内部的 clickable 已经消费了同一个指针流 —— M3E 的按压变形动效把事件吃掉，
                    // onLongPress 基本不会触发。这里改成**读组件自己的 InteractionSource**：
                    // 按下满 2.5s 就翻转纯净模式并给触感反馈，抬手时那一次 onClick 被丢弃
                    // （否则长按结束还会顺带暂停音乐）。
                    val playInteraction = remember { MutableInteractionSource() }
                    val playPressed by playInteraction.collectIsPressedAsState()
                    val haptic = LocalHapticFeedback.current
                    // true 表示这次按压已经当作"长按切纯净模式"处理，抬手的点击要忽略
                    var pureToggleConsumed by remember { mutableStateOf(false) }
                    LaunchedEffect(playPressed) {
                        if (!playPressed) return@LaunchedEffect
                        pureToggleConsumed = false
                        delay(PURE_MODE_LONG_PRESS_MS)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPureModeChange(!pureMode)
                        pureToggleConsumed = true
                    }
                    FilledIconButton(
                        onClick = {
                            if (pureToggleConsumed) {
                                // 长按刚切过纯净模式，这次抬手不当播放/暂停
                                pureToggleConsumed = false
                            } else {
                                onTogglePlay()
                            }
                        },
                        shapes = IconButtonDefaults.shapes(
                            shape = IconButtonDefaults.mediumRoundShape,
                            pressedShape = IconButtonDefaults.mediumPressedShape,
                        ),
                        modifier = Modifier.size(
                            IconButtonDefaults.mediumContainerSize(
                                IconButtonDefaults.IconButtonWidthOption.Wide,
                            ),
                        ),
                        interactionSource = playInteraction,
                        colors = IconButtonDefaults.filledIconButtonColors(),
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) {
                                "暂停（长按 2.5 秒切换纯净模式）"
                            } else {
                                "播放（长按 2.5 秒切换纯净模式）"
                            },
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                        )
                    }
                    // 下一首：与上一首同规格
                    FilledIconButton(
                        onClick = onNext,
                        shapes = IconButtonDefaults.shapes(
                            shape = IconButtonDefaults.mediumRoundShape,
                            pressedShape = IconButtonDefaults.mediumPressedShape,
                        ),
                        modifier = Modifier.size(
                            IconButtonDefaults.mediumContainerSize(
                                IconButtonDefaults.IconButtonWidthOption.Uniform,
                            ),
                        ),
                        colors = sideColors,
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "下一首",
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                        )
                    }
                }
                // 右侧：播放列表
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (!pureMode) {
                        IconButton(onClick = { showQueue = true }) {
                            Icon(Icons.Rounded.QueueMusic, contentDescription = "播放列表", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            // 功能区：收藏 / 歌词切换 / 更多菜单（下载、歌词偏移、翻译、倍速、音质并入菜单）
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
                                tint = if (fav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
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
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            },
                        )
                    }
                    // Expr v3：音质与音效入口——与同排图标同规格的 IconButton（不再用突兀的大 pill）
                    IconButton(onClick = { showAudioFxSheet = true }) {
                        Icon(
                            Icons.Rounded.Equalizer,
                            contentDescription = "音质与音效",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                    // v6：翻译开关回到功能区（此前被收进"更多"Sheet，用户要求还原）。
                    // 仅当歌词确实带翻译时出现，避免一个永久失效的按钮占位。
                    if (lyrics.hasTranslation) {
                        IconButton(
                            onClick = {
                                translationOn = !translationOn
                                onToggleTranslation()
                            },
                        ) {
                            Icon(
                                Icons.Rounded.Translate,
                                contentDescription = if (translationOn) "关闭歌词翻译" else "显示歌词翻译",
                                tint = if (translationOn) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                },
                            )
                        }
                    }
                    // 更多菜单：下载 / 歌词偏移
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
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
    // Expr：更多操作底部 Sheet（替代原 DropdownMenu——大字号全宽行，不再挤在一团）
    if (showMoreMenu && !pureMode) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
            // Expr：强制跟随沉浸主题 surface——默认 sheetContainer 在鲜艳明亮画布上会渲染成一坨黑
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    text = "更多操作",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                if (isRemoteSong && onDownload != null) {
                    SheetActionRow(
                        icon = Icons.Rounded.Download,
                        label = if (isDownloading) "正在下载…" else "下载歌曲",
                        enabled = !isDownloading,
                        onClick = {
                            showMoreMenu = false
                            onDownload?.invoke()
                        },
                    )
                }
                // Expr v4：歌词偏移直接内联在 Sheet 内调节（不再弹独立浮层）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text("歌词偏移", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "%+d ms".format(lyricsOffsetMs),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (lyricsOffsetMs != 0L) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = lyricsOffsetMs.toFloat().coerceIn(-5000f, 5000f),
                        onValueChange = { onLyricsOffsetChange(it.toLong()) },
                        valueRange = -5000f..5000f,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("-5s", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        // 微调 ±100ms，便于精确对齐
                        TextButton(onClick = { onLyricsOffsetChange(lyricsOffsetMs - 100) }) {
                            Text("-100ms", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { onLyricsOffsetChange(0L) }) {
                            Text("重置", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { onLyricsOffsetChange(lyricsOffsetMs + 100) }) {
                            Text("+100ms", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("+5s", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (lyrics.hasTranslation) {
                    SheetActionRow(
                        icon = Icons.Rounded.Translate,
                        label = "歌词翻译",
                        checked = translationOn,
                        onClick = {
                            translationOn = !translationOn
                            onToggleTranslation()
                        },
                    )
                }
            }
        }
    }
    // Expr v3：音质与音效独立 Sheet（倍速 / 音质 / EQ）
    if (showAudioFxSheet && !pureMode) {
        ModalBottomSheet(
            onDismissRequest = { showAudioFxSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text("音质与音效", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                // Expr：倍速——单行横向可滑 ButtonGroup（整合 4 个菜单项）
                Text(
                    text = "倍速播放",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                ) {
                    listOf(1f, 1.25f, 1.5f, 2f).forEach { sp ->
                        val selected = abs(state.speed - sp) < 0.01f
                        Button(
                            onClick = { onSpeedChange(sp) },
                            colors = if (selected) ButtonDefaults.buttonColors()
                                     else ButtonDefaults.buttonColors().copy(
                                         containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                         contentColor = MaterialTheme.colorScheme.onSurface),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            Text(if (sp == 1f) "1x" else "${sp}x", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                // Expr：音质——同样单行 ButtonGroup（远程歌曲才可选）
                if (isRemoteSong) {
                    Text(
                        text = "音质",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                    ) {
                        listOf(
                            Quality.Low to "低",
                            Quality.Standard to "标",
                            Quality.High to "高",
                            Quality.Lossless to "无损",
                            Quality.HiRes to "Hi-Res",
                        ).forEach { (q, label) ->
                            val selQ = currentQuality == q
                            Button(
                                onClick = { if (!selQ) onQualityChange(q) },
                                colors = if (selQ) ButtonDefaults.buttonColors()
                                         else ButtonDefaults.buttonColors().copy(
                                             containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                             contentColor = MaterialTheme.colorScheme.onSurface),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(label, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                // Expr：均衡器区块——预设快捷 chips + 10 段竖直滑杆
                EqualizerSection(
                    enabled = eqEnabled,
                    gains = eqGains,
                    customPresetsJson = eqCustomPresetsJson,
                    onToggle = onToggleEqualizer,
                    onGainsChange = onEqGainsChange,
                )
            }
        }
    }
    }
}
/** 更多操作 Sheet 的全宽动作行：大图标 + 大字号 + 可选选中勾。 */
@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.85f else 0.35f),
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.92f else 0.38f),
            modifier = Modifier.padding(start = 16.dp).weight(1f),
        )
        if (checked) {
            Icon(Icons.Rounded.Check, contentDescription = "已选",
                tint = MaterialTheme.colorScheme.primary)
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
/**
 * Expr：多点取样取色（v2）。
 * 在封面布 6 个采样点（中心 + 四象限 + 底部），每点在局部窗口内做
 * 饱和度加权平均，得到 6 个独立主色；再统一向明亮方向提升
 * （提亮度、保饱和），营造鲜艳明快的观感。
 * 返回至少 6 个互不完全相同的候选色，供流体渐变 / 沉浸画布使用。
 *
 * v5 性能：原实现逐点调 [android.graphics.Bitmap.getPixel]，每次都是一次 JNI
 * 往返（6 窗口 × 约 80 点 ≈ 500 次 JNI）。改为一次 `getPixels` 批量拷进 IntArray
 * 后纯 JVM 侧遍历，JNI 调用降到 1 次。**必须在后台线程调用。**
 */
fun android.graphics.Bitmap.extractVividPalette(): List<Int> {
    val pts = listOf(
        0.5f to 0.5f, // 中心
        0.25f to 0.25f,
        0.75f to 0.28f,
        0.22f to 0.72f,
        0.78f to 0.74f,
        0.5f to 0.9f, // 底部信息区
    )
    val win = maxOf(4, minOf(width, height) / 10)
    val out = mutableListOf<Int>()
    // 一次性批量取出全部像素（缩略图 inSampleSize=24，通常只有百来像素宽，内存可忽略）
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    for ((fx, fy) in pts) {
        val cx = (fx * width).toInt().coerceIn(1, width - 2)
        val cy = (fy * height).toInt().coerceIn(1, height - 2)
        var r = 0L; var g = 0L; var b = 0L; var wsum = 0L
        val x0 = (cx - win).coerceAtLeast(0); val x1 = (cx + win).coerceAtMost(width - 1)
        val y0 = (cy - win).coerceAtLeast(0); val y1 = (cy + win).coerceAtMost(height - 1)
        val stride = maxOf(1, win / 4)
        for (y in y0..y1 step stride) {
            val rowBase = y * width
            for (x in x0..x1 step stride) {
                val px = pixels[rowBase + x]
                val pr = (px shr 16) and 0xFF; val pg = (px shr 8) and 0xFF; val pb = px and 0xFF
                val mx = maxOf(pr, pg, pb); val mn = minOf(pr, pg, pb)
                val sat = mx - mn
                val lum = (pr * 3 + pg * 6 + pb) / 10
                // 饱和度加权 + 偏向明亮（亮像素额外加权，压暗部贡献）
                val w = (sat.toLong() * sat + 64L) * (60L + lum)
                r += pr * w; g += pg * w; b += pb * w; wsum += w
            }
        }
        if (wsum == 0L) continue
        var c = android.graphics.Color.rgb(
            (r / wsum).toInt().coerceIn(0, 255),
            (g / wsum).toInt().coerceIn(0, 255),
            (b / wsum).toInt().coerceIn(0, 255),
        )
        c = brightenVivid(c)
        if (out.none { androidx.core.graphics.ColorUtils.calculateLuminance(it) > 0 && colorDistSq(it, c) < 3000 }) {
            out.add(c)
        }
    }
    if (out.isEmpty()) out.add(android.graphics.Color.rgb(120, 160, 255))
    return out
}

/**
 * 明度规整（v6 修复：不再无条件抬饱和度）。
 *
 * 旧实现写的是 `hsv[1] = hsv[1].coerceIn(0.35f, 0.90f)` —— 把饱和度**下限强行抬到
 * 0.35**。这正是"白色封面取出粉红/淡黄"的根因：接近白/灰的采样色本身 S≈0.01~0.05，
 * 其色相 H 完全是 JPEG 色度子采样和压缩噪声决定的随机值（偏红一点就出粉，偏黄一点
 * 就出米黄）。把这种噪声色相配上 S=0.35，就等于把不存在的颜色凭空造出来并放大。
 *
 * 现在：
 * - S < 0.15 判定为**中性色**（白/灰/黑封面），保持中性，只规整明度，绝不注入色相；
 * - S >= 0.15 才是真有彩度的封面，此时把 S 收敛进 [0.30, 0.90] 避免脏暗色。
 */
private fun brightenVivid(argb: Int): Int {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    val neutral = hsv[1] < 0.15f
    if (!neutral) {
        hsv[1] = hsv[1].coerceIn(0.30f, 0.90f)
    }
    // 中性色只保证不过暗（留一点余量给对比度计算），彩色统一提到 0.62
    hsv[2] = hsv[2].coerceAtLeast(if (neutral) 0.55f else 0.62f)
    return android.graphics.Color.HSVToColor(hsv)
}

/** 简易 RGB 距离平方（去重用）。 */
private fun colorDistSq(a: Int, b: Int): Int {
    val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
    val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
    val db = (a and 0xFF) - (b and 0xFF)
    return dr * dr + dg * dg + db * db
}

/**
 * 单点主色兜底取色（保留旧逻辑：饱和度加权 + 均色明度校正，输出偏明亮）。
 */
private fun android.graphics.Bitmap.extractDominantArgb(): Int {
    val step = maxOf(1, width / 32)
    var r = 0L
    var g = 0L
    var b = 0L
    var wsum = 0L
    // 整幅均色（不加权）：用于校正明度
    var ar = 0L
    var ag = 0L
    var ab = 0L
    var acount = 0L
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
            // 饱和度加权，但极亮/极暗像素不再被丢弃，仅权重降低
            val edge = mx <= 24 || mx >= 240
            val w = (sat.toLong() * sat + if (edge) 8L else 96L)
            r += pr * w
            g += pg * w
            b += pb * w
            wsum += w
            ar += pr
            ag += pg
            ab += pb
            acount++
            x += step
        }
        y += step
    }
    if (wsum == 0L || acount == 0L) return 0
    val hue = android.graphics.Color.rgb(
        (r / wsum).toInt().coerceIn(0, 255),
        (g / wsum).toInt().coerceIn(0, 255),
        (b / wsum).toInt().coerceIn(0, 255),
    )
    val avg = android.graphics.Color.rgb(
        (ar / acount).toInt().coerceIn(0, 255),
        (ag / acount).toInt().coerceIn(0, 255),
        (ab / acount).toInt().coerceIn(0, 255),
    )
    // 40% 均色校正明度后，统一向明亮鲜艳方向提升（用户要求：取色偏明亮风）
    return brightenVivid(androidx.core.graphics.ColorUtils.blendARGB(hue, avg, 0.40f))
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
/** Expr：均衡器区块——预设快捷 chips（可横滑）+ 10 段竖直滑杆 + 总开关。 */
@Composable
private fun EqualizerSection(
    enabled: Boolean,
    gains: FloatArray,
    customPresetsJson: String,
    onToggle: (Boolean) -> Unit,
    onGainsChange: (FloatArray) -> Unit,
) {
    val bandFreqs = remember {
        listOf("31", "62", "124", "249", "498", "996", "2k", "4k", "8k", "16k")
    }
    // Poweramp 式精选预设 + 用户自定义预设（org.json 解析）
    val presets: List<Pair<String, FloatArray>> = remember(customPresetsJson) {
        val builtin = listOf(
            "平直" to FloatArray(10),
            "摇滚" to floatArrayOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f),
            "流行" to floatArrayOf(-1f, 2f, 4f, 5f, 3f, 0f, -1f, -1f, 1f, 2f),
            "舞曲" to floatArrayOf(6f, 5f, 2f, 0f, 0f, 3f, 4f, 4f, 3f, 1f),
            "电子" to floatArrayOf(5f, 4f, 1f, 0f, -2f, 2f, 1f, 2f, 5f, 6f),
            "古典" to floatArrayOf(3f, 2f, 0f, 0f, 0f, 0f, -1f, 0f, 2f, 3f),
            "低音增强" to floatArrayOf(8f, 7f, 5f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
            "人声" to floatArrayOf(-3f, -2f, 0f, 3f, 5f, 5f, 4f, 2f, 0f, -1f),
        )
        val custom = runCatching {
            val arr = org.json.JSONArray(customPresetsJson)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val ga = o.optJSONArray("gains") ?: return@mapNotNull null
                o.optString("name") to FloatArray(ga.length()) { j -> ga.optDouble(j).toFloat() }
            }
        }.getOrDefault(emptyList())
        builtin + custom
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("均衡器", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled) {
            // 预设 chips：内置+自定义，横向滑动
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                presets.forEach { (name, pgains) ->
                    val selP = gains.contentEquals(pgains)
                    Button(
                        onClick = { onGainsChange(pgains.copyOf()) },
                        colors = if (selP) ButtonDefaults.buttonColors()
                                 else ButtonDefaults.buttonColors().copy(
                                     containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                     contentColor = MaterialTheme.colorScheme.onSurface),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // Expr v4：10 段滑杆
            // - 滑条长度 96dp -> 184dp（旋转前的 width 即旋转后的可视长度）
            // - 列间距 2dp -> 10dp，左右留白 8dp -> 4dp（把空间让给滑条本体）
            // - 整组可横向滚动：10 段 * (最小列宽 + 间距) 超出窄屏时不再挤成一团
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gains.forEachIndexed { i, g ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.width(34.dp),
                    ) {
                        Text(
                            text = if (g.toInt() == 0) "0" else "%+d".format(g.toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (g != 0f) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // 竖直滑条：横向布局 184dp，再由 graphicsLayer 旋转 270 度；
                        // 外层 Box 高度 = 滑条长度，宽度 = 列宽，旋转不参与测量所以不会重叠。
                        Box(
                            modifier = Modifier.height(184.dp).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Slider(
                                value = g,
                                onValueChange = { v ->
                                    val next = gains.copyOf(); next[i] = v; onGainsChange(next)
                                },
                                valueRange = -12f..12f,
                                modifier = Modifier
                                    .width(184.dp)
                                    .graphicsLayer {
                                        rotationZ = 270f
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                                    },
                            )
                        }
                        Text(bandFreqs[i], style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

