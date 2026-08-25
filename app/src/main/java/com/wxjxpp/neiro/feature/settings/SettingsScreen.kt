package com.wxjxpp.neiro.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.data.QualityFallbackDirection
import com.wxjxpp.neiro.core.model.Quality
import com.wxjxpp.neiro.core.model.ShuffleMode
import com.wxjxpp.neiro.ui.components.ConnectedChoiceGroup
import com.wxjxpp.neiro.ui.theme.AppTheme

/** 设置页：无顶部大标题，只列可修改项。 */
@Composable
fun SettingsScreen(
    floatingPlayerBar: Boolean,
    showTranslation: Boolean,
    shuffleMode: ShuffleMode,
    neteaseCookie: String = "",
    lyricsOffsetMs: Long = 0L,
    pauseOnHeadphoneDisconnect: Boolean = true,
    pauseOnAudioFocusLoss: Boolean = true,
    ambientGlow: Boolean = false,
    onFloatingPlayerBarChange: (Boolean) -> Unit,
    onShowTranslationChange: (Boolean) -> Unit,
    onShuffleModeChange: (ShuffleMode) -> Unit,
    onNeteaseCookieChange: (String) -> Unit = {},
    onLyricsOffsetChange: (Long) -> Unit = {},
    onPauseOnHeadphoneDisconnectChange: (Boolean) -> Unit = {},
    onPauseOnAudioFocusLossChange: (Boolean) -> Unit = {},
    onAmbientGlowChange: (Boolean) -> Unit = {},
    lyricsAlign: String = "center",
    lyricsFontScale: Float = 1f,
    lyricsGapScale: Float = 1f,
    labSpringLyrics: Boolean = false,
    onLyricsAlignChange: (String) -> Unit = {},
    onLyricsFontScaleChange: (Float) -> Unit = {},
    onLyricsGapScaleChange: (Float) -> Unit = {},
    pureModeDefault: Boolean = false,
    onPureModeDefaultChange: (Boolean) -> Unit = {},
    lab8Bit: Boolean = false,
    onLab8BitChange: (Boolean) -> Unit = {},
    labTurboSpeed: Boolean = false,
    onLabTurboSpeedChange: (Boolean) -> Unit = {},
    resumeOnStart: Boolean = false,
    onResumeOnStartChange: (Boolean) -> Unit = {},
    autoPlayOnStart: Boolean = false,
    onAutoPlayOnStartChange: (Boolean) -> Unit = {},
    preferredQuality: Quality = Quality.Standard,
    onPreferredQualityChange: (Quality) -> Unit = {},
    qualityFallbackDirection: QualityFallbackDirection = QualityFallbackDirection.LOWER,
    onQualityFallbackDirectionChange: (QualityFallbackDirection) -> Unit = {},
    appFontScale: Float = 1f,
    onAppFontScaleChange: (Float) -> Unit = {},
    appFontFamily: String = "default",
    onAppFontFamilyChange: (String) -> Unit = {},
    onLabSpringLyricsChange: (Boolean) -> Unit = {},
    downloadDirUri: String = "",
    downloadEmbedCover: Boolean = true,
    downloadEmbedLyrics: Boolean = true,
    onDownloadDirChange: (String?) -> Unit = {},
    onDownloadEmbedCoverChange: (Boolean) -> Unit = {},
    onDownloadEmbedLyricsChange: (Boolean) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    val context = androidx.compose.ui.platform.LocalContext.current
    // 二级菜单：null=根列表；否则进入对应子页
    var subsection by remember { mutableStateOf<String?>(null) }
    // 子页返回手势：先回设置根页，再走外壳的页面级返回
    androidx.activity.compose.BackHandler(enabled = subsection != null) { subsection = null }
    // 二级导航转场：Material Motion「Transition Choreography」Shared Axis Y——
    // 进入子页=前进（新内容自下而上推入），返回=后退（旧内容向下滑出），
    // 位移走弹簧、透明度走 effects 规格，与全局路由编排同一套动效语言
    val enterAxisSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val exitAxisSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = subsection,
        transitionSpec = {
            val forward = initialState == null
            val enterY: (Int) -> Int = if (forward) { it -> it / 6 } else { it -> -it / 6 }
            val exitY: (Int) -> Int = if (forward) { it -> -it / 4 } else { it -> it / 4 }
            (
                slideInVertically(enterAxisSpring, enterY) + fadeIn(effectsSpec)
            ) togetherWith (
                slideOutVertically(exitAxisSpring, exitY) + fadeOut(effectsSpec)
            )
        },
        label = "settingsSubsection",
        modifier = modifier,
    ) { currentSubsection ->
        if (currentSubsection != null) {
            SettingsSubsection(
                title = when (currentSubsection) {
                    "lyrics" -> "歌词"
                    "playback" -> "播放"
                    "source" -> "音源"
                    "appearance" -> "外观"
                    "download" -> "下载"
                    "lab" -> "实验室"
                    else -> ""
                },
                onBack = { subsection = null },
                contentPadding = contentPadding,
            ) {
                when (currentSubsection) {
                "lyrics" -> {
                    SwitchRow(
                        title = "显示翻译",
                        subtitle = "歌词下方显示译文（若歌词包含）",
                        checked = showTranslation,
                        onCheckedChange = onShowTranslationChange,
                    )
                    var offsetValue by remember(lyricsOffsetMs) { mutableStateOf(lyricsOffsetMs.toFloat()) }
                    Text(
                        text = "歌词偏移：%+.1f 秒（正数提前 / 负数延后）".format(offsetValue / 1000f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppTheme.dimens.spaceSm),
                    )
                    Slider(
                        value = offsetValue.coerceIn(-5000f, 5000f),
                        onValueChange = { offsetValue = it },
                        onValueChangeFinished = { onLyricsOffsetChange(offsetValue.toLong()) },
                        valueRange = -5000f..5000f,
                    )
                    Text(
                        text = "对齐方式",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = AppTheme.dimens.spaceMd, bottom = AppTheme.dimens.spaceXs),
                    )
                    val alignOptions = remember { listOf("start" to "左", "center" to "中", "end" to "右") }
                    ConnectedChoiceGroup(
                        options = alignOptions.map { it.second },
                        selectedIndex = alignOptions.indexOfFirst { it.first == lyricsAlign }.coerceAtLeast(0),
                        onSelect = { index -> onLyricsAlignChange(alignOptions[index].first) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = AppTheme.dimens.spaceMd))
                    var fontValue by remember(lyricsFontScale) { mutableStateOf(lyricsFontScale) }
                    Text(
                        text = "歌词字号：%.0f%%".format(fontValue * 100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = fontValue,
                        onValueChange = { fontValue = it },
                        onValueChangeFinished = { onLyricsFontScaleChange(fontValue) },
                        valueRange = 0.7f..1.6f,
                    )
                    var gapValue by remember(lyricsGapScale) { mutableStateOf(lyricsGapScale) }
                    Text(
                        text = "行间隙：%.0f%%".format(gapValue * 100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = gapValue,
                        onValueChange = { gapValue = it },
                        onValueChangeFinished = { onLyricsGapScaleChange(gapValue) },
                        valueRange = 0.5f..2.0f,
                    )
                }
                "playback" -> {
                    Text(
                        text = "伪随机一轮内不重复；真随机每次独立掷骰，可能连续重复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AppTheme.dimens.spaceSm),
                    )
                    ConnectedChoiceGroup(
                        options = ShuffleMode.entries.map { if (it == ShuffleMode.Pseudo) "伪随机" else "真随机" },
                        selectedIndex = ShuffleMode.entries.indexOf(shuffleMode).coerceAtLeast(0),
                        onSelect = { index -> onShuffleModeChange(ShuffleMode.entries[index]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = AppTheme.dimens.spaceMd))
                    SwitchRow(
                        title = "拔出耳机自动暂停",
                        subtitle = "断开有线耳机或蓝牙音频时自动暂停播放",
                        checked = pauseOnHeadphoneDisconnect,
                        onCheckedChange = onPauseOnHeadphoneDisconnectChange,
                    )
                    SwitchRow(
                        title = "其他应用发声时暂停",
                        subtitle = "其他应用抢占音频焦点时暂停本应用播放",
                        checked = pauseOnAudioFocusLoss,
                        onCheckedChange = onPauseOnAudioFocusLossChange,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = AppTheme.dimens.spaceMd))
                    SwitchRow(
                        title = "记录上次播放",
                        subtitle = "退出应用时记住听到哪，下次打开恢复到该位置（暂停）",
                        checked = resumeOnStart,
                        onCheckedChange = onResumeOnStartChange,
                    )
                    SwitchRow(
                        title = "启动自动播放",
                        subtitle = "打开应用后自动继续上次的歌曲（需先开启记录上次播放）",
                        checked = autoPlayOnStart,
                        onCheckedChange = onAutoPlayOnStartChange,
                    )
                }
                "appearance" -> {
                    SwitchRow(
                        title = "悬浮播放栏",
                        subtitle = "将底部播放栏显示为悬浮卡片",
                        checked = floatingPlayerBar,
                        onCheckedChange = onFloatingPlayerBarChange,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = AppTheme.dimens.spaceMd))
                    Text(
                        text = "字体样式（全应用生效）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = AppTheme.dimens.spaceXs),
                    )
                    val fontOptions = remember { listOf("default" to "默认", "serif" to "衬线", "mono" to "等宽") }
                    ConnectedChoiceGroup(
                        options = fontOptions.map { it.second },
                        selectedIndex = fontOptions.indexOfFirst { it.first == appFontFamily }.coerceAtLeast(0),
                        onSelect = { index -> onAppFontFamilyChange(fontOptions[index].first) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    var fontScaleValue by remember(appFontScale) { mutableStateOf(appFontScale) }
                    Text(
                        text = "字号大小：%.0f%%".format(fontScaleValue * 100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppTheme.dimens.spaceMd),
                    )
                    Slider(
                        value = fontScaleValue,
                        onValueChange = { fontScaleValue = it },
                        onValueChangeFinished = { onAppFontScaleChange(fontScaleValue) },
                        valueRange = 0.8f..1.4f,
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("80%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.material3.TextButton(onClick = {
                            fontScaleValue = 1f
                            onAppFontScaleChange(1f)
                        }) { Text("重置") }
                        Text("140%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                "download" -> {
                    // SAF 目录选择：授权一次，后续下载自动写入
                    val dirPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocumentTree(),
                    ) { uri ->
                        if (uri != null) {
                            runCatching {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                )
                            }
                            onDownloadDirChange(uri.toString())
                        }
                    }
                    Text(
                        text = "下载目录",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = AppTheme.dimens.spaceXs),
                    )
                    val dirDisplay = when {
                        downloadDirUri.isEmpty() -> "默认（Music/Neiro）"
                        else -> runCatching {
                            androidx.documentfile.provider.DocumentFile
                                .fromTreeUri(context, android.net.Uri.parse(downloadDirUri))
                                ?.uri?.lastPathSegment ?: "已选择目录"
                        }.getOrDefault("已选择目录")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { dirPicker.launch(null) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(dirDisplay, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "点按选择自定义下载位置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { dirPicker.launch(null) }) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = "选择目录")
                        }
                    }
                    if (downloadDirUri.isNotEmpty()) {
                        androidx.compose.material3.TextButton(onClick = { onDownloadDirChange(null) }) {
                            Text("恢复默认目录")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = AppTheme.dimens.spaceMd))
                    SwitchRow(
                        title = "嵌入专辑封面",
                        subtitle = "把专辑图写进音频文件的标签里",
                        checked = downloadEmbedCover,
                        onCheckedChange = onDownloadEmbedCoverChange,
                    )
                    SwitchRow(
                        title = "嵌入歌词",
                        subtitle = "把 LRC 歌词写进音频文件（MP3/M4A/FLAC 均支持）",
                        checked = downloadEmbedLyrics,
                        onCheckedChange = onDownloadEmbedLyricsChange,
                    )
                }
                "source" -> {
                    Text(
                        text = "当前在线播放音质",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = AppTheme.dimens.spaceXs),
                    )
                    ConnectedChoiceGroup(
                        options = Quality.entries.map { qualityLabel(it) },
                        selectedIndex = Quality.entries.indexOf(preferredQuality).coerceAtLeast(0),
                        onSelect = { index -> onPreferredQualityChange(Quality.entries[index]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "取流失败时按下面的方向逐级调整音质重试；只使用歌曲自身平台的音源，不会跨平台自动换源。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppTheme.dimens.spaceSm),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = AppTheme.dimens.spaceMd))
                    Text(
                        text = "音质回退方向（同一首歌逐级调整音质重试）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = AppTheme.dimens.spaceXs),
                    )
                    val fallbackOptions = remember {
                        listOf(
                            QualityFallbackDirection.LOWER to "优先降低",
                            QualityFallbackDirection.HIGHER to "优先升高",
                        )
                    }
                    ConnectedChoiceGroup(
                        options = fallbackOptions.map { it.second },
                        selectedIndex = fallbackOptions.indexOfFirst { it.first == qualityFallbackDirection }.coerceAtLeast(0),
                        onSelect = { index -> onQualityFallbackDirectionChange(fallbackOptions[index].first) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = if (qualityFallbackDirection == QualityFallbackDirection.LOWER) {
                            "降低：Lossless → High → Standard → Low，流量友好"
                        } else {
                            "升高：Low → Standard → High → Lossless → HiRes，音质优先"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppTheme.dimens.spaceSm),
                    )
                }
                "lab" -> {
                    Text(
                        text = "实验性功能可能不稳定，随时可能更改或移除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AppTheme.dimens.spaceSm),
                    )
                    SwitchRow(
                        title = "动态流光背景",
                        subtitle = "播放页根据封面主色呈现流动渐变背景（实验性）",
                        checked = ambientGlow,
                        onCheckedChange = onAmbientGlowChange,
                    )
                    SwitchRow(
                        title = "歌词弹簧动效",
                        subtitle = "切句与翻译切换时使用带回弹的弹簧动画，更加灵动",
                        checked = labSpringLyrics,
                        onCheckedChange = onLabSpringLyricsChange,
                    )
                    SwitchRow(
                        title = "纯净模式默认开启",
                        subtitle = "进入播放页时直接隐藏辅助控件（长按播放键也可临时开启）",
                        checked = pureModeDefault,
                        onCheckedChange = onPureModeDefaultChange,
                    )
                    SwitchRow(
                        title = "8bit 播放模式",
                        subtitle = "把音频量化为 8-bit，复古游戏机音质",
                        checked = lab8Bit,
                        onCheckedChange = onLab8BitChange,
                    )
                    SwitchRow(
                        title = "80 倍速播放模式",
                        subtitle = "超高速播放（实验性音效，慎开）",
                        checked = labTurboSpeed,
                        onCheckedChange = onLabTurboSpeedChange,
                    )
                    }
                }
            }
        } else {
            // 根列表：同样置于编排容器内，与子页构成 Shared Axis Y 的转场两端
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(horizontal = dimens.spaceLg),
            ) {
        // 导航入口：打开侧边栏（每个页面必须有导航方式）
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Rounded.Menu, contentDescription = "打开导航")
            }
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }
        // 二级菜单入口组：每项用 ListItem（Item）包裹，整组卡片收拢
        androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SubsectionEntry(title = "歌词", icon = Icons.Rounded.Lyrics) { subsection = "lyrics" }
            SubsectionEntry(title = "播放", icon = Icons.Rounded.PlayCircle) { subsection = "playback" }
            SubsectionEntry(title = "音源", icon = Icons.Rounded.GraphicEq) { subsection = "source" }
            SubsectionEntry(title = "外观", icon = Icons.Rounded.Palette) { subsection = "appearance" }
            SubsectionEntry(title = "下载", icon = Icons.Rounded.Download) { subsection = "download" }
            SubsectionEntry(title = "实验室", icon = Icons.Rounded.Science) { subsection = "lab" }
            } // Card 入口组
        } // 根列表 Column
    } // else：根列表分支
} // AnimatedContent 内容
} // SettingsScreen

/** 二级子页：带返回键的简单容器。 */
@Composable
private fun SettingsSubsection(
    title: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = AppTheme.dimens.spaceLg),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(AppTheme.dimens.spaceSm))
        content()
    }
}

/** 音质档位的短标签（设置页用）。 */
internal fun qualityLabel(q: Quality): String = when (q) {
    Quality.Low -> "低"
    Quality.Standard -> "标"
    Quality.High -> "高"
    Quality.Lossless -> "无"
    Quality.HiRes -> "Hi"
}

/** 根列表里的一行菜单入口：ListItem（Item）样式，图标 + 名称 + 右箭头。 */
@Composable
private fun SubsectionEntry(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = AppTheme.dimens.spaceLg, bottom = AppTheme.dimens.spaceXs),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}