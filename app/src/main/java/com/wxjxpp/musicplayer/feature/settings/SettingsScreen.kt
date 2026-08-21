package com.wxjxpp.musicplayer.feature.settings

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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wxjxpp.musicplayer.core.model.ShuffleMode
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/** 设置页：无顶部大标题，只列可修改项。 */
@Composable
fun SettingsScreen(
    floatingPlayerBar: Boolean,
    showTranslation: Boolean,
    shuffleMode: ShuffleMode,
    onFloatingPlayerBarChange: (Boolean) -> Unit,
    onShowTranslationChange: (Boolean) -> Unit,
    onShuffleModeChange: (ShuffleMode) -> Unit,
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