package com.wxjxpp.neiro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.app.Banner
import kotlinx.coroutines.delay

/**
 * 全局横幅栈：顶部安全区内堆叠展示最新 2 条。
 *
 * - 成功绿 / 失败红 / 信息中性，自动按类型着色
 * - 新条目滑入展开；关闭（手动或超时）收起后再从队列移除，露出下一条
 * - 手动点 ✕ 与自动超时共用同一套退出动画
 */
@Composable
fun BannerStack(
    banners: List<Banner>,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    /** 已触发退场动画、等待从队列移除的条目。 */
    val hiding = remember { mutableStateMapOf<Long, Boolean>() }
    Column(
        modifier = modifier
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        banners.takeLast(2).forEach { banner ->
            key(banner.id) {
                // 自动消失：错误停留更久（明细需要阅读时间）
                LaunchedEffect(banner.id) {
                    delay(if (banner.type == "error") 12_000L else 4_000L)
                    hiding[banner.id] = true
                }
                LaunchedEffect(hiding[banner.id] == true) {
                    if (hiding[banner.id] == true) {
                        delay(260) // 等退出动画播完再真正出队
                        onDismiss(banner.id)
                    }
                }
                AnimatedVisibility(
                    visible = hiding[banner.id] != true,
                    enter = slideInVertically(tween(280)) { -it } +
                        expandVertically(tween(280)) +
                        fadeIn(tween(220)),
                    exit = slideOutVertically(tween(220)) { -it } +
                        shrinkVertically(tween(220)) +
                        fadeOut(tween(160)),
                ) {
                    BannerCard(
                        banner = banner,
                        onClose = { hiding[banner.id] = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerCard(
    banner: Banner,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val container: androidx.compose.ui.graphics.Color
    val content: androidx.compose.ui.graphics.Color
    val border: androidx.compose.ui.graphics.Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    when (banner.type) {
        // 成功绿：主色低透明度铺底（暗色模式自适应）
        "success" -> {
            container = cs.primary.copy(alpha = 0.16f)
            content = cs.primary
            border = cs.primary.copy(alpha = 0.34f)
            icon = Icons.Rounded.CheckCircle
        }
        // 失败红
        "error" -> {
            container = cs.errorContainer
            content = cs.onErrorContainer
            border = cs.error.copy(alpha = 0.42f)
            icon = Icons.Rounded.WarningAmber
        }
        // 中性信息
        else -> {
            container = cs.surfaceVariant.copy(alpha = 0.92f)
            content = cs.onSurfaceVariant
            border = cs.outlineVariant
            icon = Icons.Rounded.Info
        }
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        var expanded by remember(banner.id) { androidx.compose.runtime.mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = banner.message,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).heightIn(min = 24.dp),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
