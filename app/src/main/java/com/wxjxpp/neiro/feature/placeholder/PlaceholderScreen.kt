package com.wxjxpp.neiro.feature.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 功能占位页。
 *
 * 抽屉里每个入口都先指向这里，路由与导航已经打通，
 * 后续把 [title] 对应的真实页面替换进 AppNavHost 即可。
 */
@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
) {
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.spaceXl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Rounded.Menu, contentDescription = "打开导航")
        }
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = dimens.spaceSm),
        )
    }
}