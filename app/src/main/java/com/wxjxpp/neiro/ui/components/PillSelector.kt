package com.wxjxpp.neiro.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 连通式胶囊单选组（MD3E 风格）。
 *
 * 所有选项首尾相接成一条胶囊带：选中的填充主色、文字反白；
 * 未选中透底描边。相邻项之间以 1dp 分隔线区隔。
 * 用于「歌曲 / 发现 / 一起听」等页面的搜索分类标签，统一交互与外观。
 */
@Composable
fun PillSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = modifier.height(34.dp)) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            // 连通胶囊：中间项直角、两端半圆
            val shape: Shape = when {
                options.size == 1 -> RoundedCornerShape(50)
                index == 0 -> RoundedCornerShape(
                    topStart = 50, bottomStart = 50, topEnd = 0, bottomEnd = 0,
                )
                index == options.lastIndex -> RoundedCornerShape(
                    topStart = 0, bottomStart = 0, topEnd = 50, bottomEnd = 50,
                )
                else -> RoundedCornerShape(0)
            }
            Surface(
                shape = shape,
                color = if (selected) cs.primary else cs.surface,
                contentColor = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                border = BorderStroke(1.dp, if (selected) cs.primary else cs.outlineVariant),
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onSelect(index) },
            ) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            // 相邻分隔线（不画在末尾）
            if (index != options.lastIndex) {
                Surface(color = cs.outlineVariant, modifier = Modifier.width(1.dp)) {}
            }
        }
    }
}