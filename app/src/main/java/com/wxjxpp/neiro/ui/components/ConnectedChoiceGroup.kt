@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.wxjxpp.neiro.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 连通式单选选择组（官方 M3E Connected Button Group 实现）。
 *
 * 基于 Material 3 Expressive 官方 [ButtonGroup] 连通变体：
 * - 首项/中间项/末项分别套用 [ButtonGroupDefaults.connectedLeadingButtonShapes] /
 *   connectedMiddleButtonShapes / connectedTrailingButtonShapes 官方连通形状，
 *   以 [ButtonGroupDefaults.ConnectedSpaceBetween] 官方间距连成一条胶囊带；
 * - 每一项都是标准 [ToggleButton]，按压形状变形、选中态配色全部由组件库规范接管；
 * - 空间不足时自动溢出进官方下拉菜单（[ButtonGroupDefaults.OverflowIndicator]），
 *   溢出条目与栏内条目行为一致；
 * - 外层横向滚动兜底：选项过多时优先滚动而非溢出。
 *
 * 注意：官方连通形状函数为 @Composable，故在组合期预先解析三种形状，
 * 再传入 ButtonGroup 的声明式内容作用域。
 */
@Composable
fun ConnectedChoiceGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 官方连通形状（组合上下文内解析）
    val leadingShapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
    val middleShapes = ButtonGroupDefaults.connectedMiddleButtonShapes()
    val trailingShapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
    Box(modifier.height(40.dp).horizontalScroll(rememberScrollState())) {
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
                    index == 0 -> leadingShapes
                    index == options.lastIndex -> trailingShapes
                    else -> middleShapes
                }
                customItem(
                    buttonGroupContent = {
                        ToggleButton(
                            checked = selected,
                            onCheckedChange = { onSelect(index) },
                            shapes = shapes,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    },
                    menuContent = { state ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSelect(index)
                                state.dismiss()
                            },
                        )
                    },
                )
            }
        }
    }
}