package com.wxjxpp.neiro.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 顶栏内嵌 SearchBar 胶囊（歌曲页/发现页共用）。
 *
 * - 外观与搜索页 Material3 [androidx.compose.material3.SearchBar] 收起态同规格：
 *   56dp 高、28dp 圆角、surfaceContainerHigh、24dp 放大镜 + 同款 placeholder；
 * - 点击后在本容器内弹簧放大 + 微模糊作为点击反馈，320ms 后触发 [onSearch]
 *   切页——长宽由 sharedBounds（key="search_bar"）跨页真·变形（容器变换规范）；
 * - LocalRouteAnimScope / LocalSharedTransitionScope 缺失时静默退化为普通跳转。
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun TopBarSearchPill(
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "歌名 / 歌手 / 专辑 / 标签",
) {
    var launching by remember { mutableStateOf(false) }
    val expand by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (launching) 1.1f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.62f,
            stiffness = 260f,
        ),
        label = "searchBarExpand",
    )
    val glow by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (launching) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(280),
        label = "searchBarGlow",
    )
    LaunchedEffect(launching) {
        if (launching) {
            delay(320)
            onSearch()
        }
    }
    Surface(
        onClick = { if (!launching) launching = true },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (launching) 10.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                LocalRouteAnimScope.current?.let { animScope ->
                    LocalSharedTransitionScope.current?.let { sts ->
                        with(sts) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "search_bar"),
                                animatedVisibilityScope = animScope,
                            )
                        }
                    }
                } ?: Modifier
            )
            .graphicsLayer {
                scaleX = expand; scaleY = expand
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp),
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
                    .copy(alpha = 1f - glow * 0.5f),
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}