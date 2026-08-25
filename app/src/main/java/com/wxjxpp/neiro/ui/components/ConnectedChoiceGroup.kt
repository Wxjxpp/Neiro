@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.wxjxpp.neiro.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupMenuState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 连通式单选选择组（官方实现）。
 *
 * 基于 Material 3 Expressive 的 [ButtonGroup] 连通变体（connected button group）：
 * - 首项/中间项/末项分别使用 [ButtonGroupDefaults.connectedLeadingButtonShapes] /
 *   connectedMiddleButtonShapes / connectedTrailingButtonShapes 官方形状，
 *   首尾半圆、中间直角，以 [ButtonGroupDefaults.ConnectedSpaceBetween] 微间距连成一条胶囊带；
 * - 每一项是标准 [ToggleButton]：选中态着色、按压形状变形均由组件库规范接管；
 * - 通过 [ButtonGroupScope.animateWidth] 接入官方按压宽度扩展动效（按下放大、相邻压缩）；
 * - 空间不足时自动溢出进官方下拉菜单（[ButtonGroupDefaults.OverflowIndicator]）；
 * - 组件自带横向滚动兜底，选项过多时不撑破父布局。
 */
@Composable
fun ConnectedChoiceGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.height(40.dp).horizontalScroll(rememberScrollState())) {
        val menuState = remember { ButtonGroupMenuState() }
        ButtonGroup(
            overflowIndicator = { state ->
                ButtonGroupDefaults.OverflowIndicator(menuState = state)
            },
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                // 连通形状：首项半圆起点、末项半圆终点、中间直角相连
                val shapes = when {
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
                val interactionSource = remember(index) { MutableInteractionSource() }
                ToggleButton(
                    checked = selected,
                    onCheckedChange = { onSelect(index) },
                    shapes = shapes,
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .animateWidth(interactionSource),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
