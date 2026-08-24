package com.wxjxpp.neiro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 列表悬浮操作组：一键回顶 + （可选）定位当前播放。
 *
 * - 下滑超过一屏后出现「回顶」按钮，点击平滑滚回第 0 项；
 * - [onLocate] 非空时显示「定位」按钮（把当前播放歌滚进可视区并短暂高亮由页面自行处理）；
 * - 调用方用 modifier 控制摆放位置（一般 align 到 BottomEnd 并留出播放栏高度）。
 */
@Composable
fun ScrollActions(
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLocate: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val showTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 6 }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = onLocate != null && showTop,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            SmallFloatingActionButton(onClick = { onLocate?.invoke() }) {
                Icon(Icons.Rounded.MyLocation, contentDescription = "定位当前播放")
            }
        }
        Spacer(Modifier.height(10.dp))
        AnimatedVisibility(
            visible = showTop,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            SmallFloatingActionButton(onClick = {
                scope.launch { listState.animateScrollToItem(0) }
            }) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "回到顶部")
            }
        }
    }
}