package com.wxjxpp.neiro.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
    ambientGlow: Boolean = true,
    onFloatingPlayerBarChange: (Boolean) -> Unit,
    onShowTranslationChange: (Boolean) -> Unit,
    onShuffleModeChange: (ShuffleMode) -> Unit,
    onNeteaseCookieChange: (String) -> Unit = {},
    onLyricsOffsetChange: (Long) -> Unit = {},
    onPauseOnHeadphoneDisconnectChange: (Boolean) -> Unit = {},
    onPauseOnAudioFocusLossChange: (Boolean) -> Unit = {},
    onAmbientGlowChange: (Boolean) -> Unit = {},
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = dimens.spaceLg),
    ) {
        SectionTitle("播放栏")
        SwitchRow(
            title = "悬浮播放栏",
            subtitle = "将底部播放栏显示为悬浮卡片",
            checked = floatingPlayerBar,
            onCheckedChange = onFloatingPlayerBarChange,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = dimens.spaceSm))

        SectionTitle("歌词")
        SwitchRow(
            title = "显示翻译",
            subtitle = "歌词下方显示译文（若歌词包含）",
            checked = showTranslation,
            onCheckedChange = onShowTranslationChange,
        )
        // 歌词偏移（滑杆，±2000ms）
        var offsetValue by remember(lyricsOffsetMs) {
            mutableStateOf(lyricsOffsetMs.toFloat())
        }
        Text(
            text = "歌词偏移：%+d ms（正数提前 / 负数延后）".format(offsetValue.toInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = dimens.spaceSm),
        )
        Slider(
            value = offsetValue.coerceIn(-50f, 50f),
            onValueChange = { offsetValue = it },
            onValueChangeFinished = { onLyricsOffsetChange(offsetValue.toLong()) },
            valueRange = -50f..50f,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = dimens.spaceSm))

        SectionTitle("播放行为")
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
        HorizontalDivider(modifier = Modifier.padding(vertical = dimens.spaceSm))

        SectionTitle("外观")
        SwitchRow(
            title = "动态流光背景",
            subtitle = "播放页根据封面主色呈现流动渐变背景",
            checked = ambientGlow,
            onCheckedChange = onAmbientGlowChange,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = dimens.spaceSm))

        SectionTitle("随机播放")
        Text(
            text = "伪随机一轮内不重复；真随机每次独立掷骰，可能连续重复",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = dimens.spaceSm),
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