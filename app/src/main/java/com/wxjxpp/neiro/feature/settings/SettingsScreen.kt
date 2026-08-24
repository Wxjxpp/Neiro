package com.wxjxpp.neiro.feature.settings

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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
    onOpenDrawer: () -> Unit = {},
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    // 二级菜单：null=根列表；否则进入对应子页
    var subsection by remember { mutableStateOf<String?>(null) }
    // 子页返回手势：先回设置根页，再走外壳的页面级返回
    androidx.activity.compose.BackHandler(enabled = subsection != null) { subsection = null }
    if (subsection != null) {
        SettingsSubsection(
            title = when (subsection) {
                "lyrics" -> "歌词"
                "playback" -> "播放"
                "source" -> "音源"
                "appearance" -> "外观"
                "lab" -> "实验室"
                else -> ""
            },
            onBack = { subsection = null },
            contentPadding = contentPadding,
            modifier = modifier,
        ) {
            when (subsection) {
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("start" to "左", "center" to "中", "end" to "右").forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = lyricsAlign == value,
                                onClick = { onLyricsAlignChange(value) },
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                                label = { Text(label) },
                            )
                        }
                    }
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ShuffleMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = shuffleMode == mode,
                                onClick = { onShuffleModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ShuffleMode.entries.size),
                                label = { Text(if (mode == ShuffleMode.Pseudo) "伪随机" else "真随机") },
                            )
                        }
                    }
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "default" to "默认",
                            "serif" to "衬线",
                            "mono" to "等宽",
                        ).forEachIndexed { index, (id, label) ->
                            SegmentedButton(
                                selected = appFontFamily == id,
                                onClick = { onAppFontFamilyChange(id) },
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                                label = { Text(label) },
                            )
                        }
                    }
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
                "source" -> {
                    Text(
                        text = "当前在线播放音质",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = AppTheme.dimens.spaceXs),
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        Quality.entries.forEachIndexed { index, q ->
                            SegmentedButton(
                                selected = preferredQuality == q,
                                onClick = { onPreferredQualityChange(q) },
                                shape = SegmentedButtonDefaults.itemShape(index, Quality.entries.size),
                                label = { Text(qualityLabel(q)) },
                            )
                        }
                    }
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            QualityFallbackDirection.LOWER to "优先降低",
                            QualityFallbackDirection.HIGHER to "优先升高",
                        ).forEachIndexed { index, (direction, label) ->
                            SegmentedButton(
                                selected = qualityFallbackDirection == direction,
                                onClick = { onQualityFallbackDirectionChange(direction) },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                label = { Text(label) },
                            )
                        }
                    }
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
        return
    }
    Column(
        modifier = modifier
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
        SubsectionEntry(title = "歌词", icon = Icons.Rounded.Lyrics) { subsection = "lyrics" }
        SubsectionEntry(title = "播放", icon = Icons.Rounded.PlayCircle) { subsection = "playback" }
        SubsectionEntry(title = "音源", icon = Icons.Rounded.GraphicEq) { subsection = "source" }
        SubsectionEntry(title = "外观", icon = Icons.Rounded.Palette) { subsection = "appearance" }
        SubsectionEntry(title = "实验室", icon = Icons.Rounded.Science) { subsection = "lab" }
        HorizontalDivider(modifier = Modifier.padding(vertical = dimens.spaceSm))
        SectionTitle("在线播放")
        Text(
            text = "网易云 Cookie（可选）。填入网页版登录后的 MUSIC_U 值可解锁 VIP / 无版权歌曲，" +
                "格式：MUSIC_U=xxxxxxxx。免费歌曲无需 Cookie。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = dimens.spaceSm),
        )
        var cookieText by remember(neteaseCookie) { mutableStateOf(neteaseCookie) }
        OutlinedTextField(
            value = cookieText,
            onValueChange = {
                cookieText = it
                onNeteaseCookieChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("MUSIC_U=...") },
        )
    }
}

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

/** 根列表里的一行菜单入口：只写名称 + 前置图标（不写解释，紧凑）。 */
@Composable
private fun SubsectionEntry(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = AppTheme.dimens.spaceLg),
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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