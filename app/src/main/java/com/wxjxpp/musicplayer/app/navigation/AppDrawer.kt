package com.wxjxpp.musicplayer.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/**
 * 侧边功能菜单项。
 *
 * 新增功能只要往 [drawerItems] 加一条，抽屉与路由自动同步，
 * 不需要改抽屉 UI 代码。
 */
data class DrawerItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector,
)

val drawerItems: List<DrawerItem> = listOf(
    DrawerItem(Destination.Home, "首页", Icons.Filled.Home),
    DrawerItem(Destination.Search, "搜索", Icons.Filled.Search),
    DrawerItem(Destination.Library, "本地曲库", Icons.Filled.LibraryMusic),
    DrawerItem(Destination.Playlists, "歌单", Icons.Filled.QueueMusic),
    DrawerItem(Destination.Diary, "听歌日记", Icons.Filled.Book),
    DrawerItem(Destination.Together, "一起听", Icons.Filled.Groups),
    DrawerItem(Destination.Report, "年度报告", Icons.Filled.Insights),
    DrawerItem(Destination.Settings, "设置", Icons.Filled.Settings),
)

@Composable
fun AppDrawerSheet(
    currentRoute: String?,
    onNavigate: (Destination) -> Unit,
) {
    val dimens = AppTheme.dimens
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.spaceMd),
        ) {
            Spacer(Modifier.height(dimens.spaceXl))
            Text(
                text = "Music Player",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = dimens.spaceMd),
            )
            Spacer(Modifier.height(dimens.spaceLg))
            HorizontalDivider()
            Spacer(Modifier.height(dimens.spaceSm))

            drawerItems.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.label) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    selected = currentRoute == item.destination.route,
                    onClick = { onNavigate(item.destination) },
                    modifier = Modifier.padding(vertical = dimens.spaceXs),
                )
            }
            Spacer(Modifier.height(dimens.spaceLg))
        }
    }
}