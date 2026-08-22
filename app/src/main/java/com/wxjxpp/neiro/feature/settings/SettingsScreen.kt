package com.wxjxpp.neiro.feature.settings

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
    onLabSpringLyricsChange: (Boolean) -> Unit = {},
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    // 二级菜单：null=根列表；否则进入对应子页
    var subsection by remember { mutableStateOf<String?>(null) }
    if (subsection != null) {
        SettingsSubsection(
            title = when (subsection) {
                "lyrics" -> "歌词"
                "playback" -> "播放"
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
                        text = "歌词偏移：%+d ms（正数提前 / 负数延后）".format(offsetValue.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppTheme.dimens.spaceSm),
                    )
                    Slider(
                        value = offsetValue.coerceIn(-50f, 50f),
                        onValueChange = { offsetValue = it },
                        onValueChangeFinished = { onLyricsOffsetChange(offsetValue.toLong()) },
                        valueRange = -50f..50f,
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
                }
                "appearance" -> {
                    SwitchRow(
                        title = "悬浮播放栏",
                        subtitle = "将底部播放栏显示为悬浮卡片",
                        checked = floatingPlayerBar,
                        onCheckedChange = onFloatingPlayerBarChange,
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
        SubsectionEntry(title = "歌词", subtitle = "翻译 / 偏移 / 对齐方式") { subsection = "lyrics" }
        SubsectionEntry(title = "播放", subtitle = "随机模式 / 耳机与音频焦点") { subsection = "playback" }
        SubsectionEntry(title = "外观", subtitle = "悬浮播放栏") { subsection = "appearance" }
        SubsectionEntry(title = "实验室", subtitle = "流光背景 / 弹簧动效 / 纯净模式") { subsection = "lab" }
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(AppTheme.dimens.spaceSm))
        content()
    }
}

/** 根列表里的一行菜单入口。 */
@Composable
private fun SubsectionEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppTheme.dimens.spaceMd),
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
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
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