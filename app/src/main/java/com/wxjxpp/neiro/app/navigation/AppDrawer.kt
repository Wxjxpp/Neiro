package com.wxjxpp.neiro.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.wxjxpp.neiro.ui.theme.AppTheme
import java.time.LocalDate

/** 侧边抽屉菜单项。搜索不在此处：它仅由歌曲页右上角入口打开。 */
data class DrawerItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector,
    val group: DrawerGroup,
)

enum class DrawerGroup { Library, Personal, System }

/** 年度报告仅在 12 月 30 日至次年 1 月 13 日开放。 */
fun isAnnualReportAvailable(today: LocalDate = LocalDate.now()): Boolean {
    val month = today.monthValue
    val day = today.dayOfMonth
    return (month == 12 && day >= 30) || (month == 1 && day <= 13)
}

fun drawerItems(today: LocalDate = LocalDate.now()): List<DrawerItem> = buildList {
    add(DrawerItem(Destination.Home, "歌曲", Icons.Rounded.MusicNote, DrawerGroup.Library))
    add(DrawerItem(Destination.Albums, "专辑", Icons.Rounded.Album, DrawerGroup.Library))
    add(DrawerItem(Destination.Playlists, "歌单", Icons.Rounded.QueueMusic, DrawerGroup.Library))
    add(DrawerItem(Destination.Discover, "发现", Icons.Rounded.Explore, DrawerGroup.Library))
    add(DrawerItem(Destination.MusicSources, "自定义音源", Icons.Rounded.Extension, DrawerGroup.Library))
    add(DrawerItem(Destination.Diary, "听歌日记", Icons.Rounded.Book, DrawerGroup.Personal))
    add(DrawerItem(Destination.Together, "一起听", Icons.Rounded.Groups, DrawerGroup.Personal))
    if (isAnnualReportAvailable(today)) {
        add(DrawerItem(Destination.Report, "年度报告", Icons.Rounded.Insights, DrawerGroup.Personal))
    }
    add(DrawerItem(Destination.Settings, "设置", Icons.Rounded.Settings, DrawerGroup.System))
}

/**
 * 侧边抽屉。
 *
 * 紧凑布局：条目高度压缩（vertical padding 0 + 默认 item 高度），
 * 分组之间只用一条细分割线，不再留大段空白。
 * 安全区由 ModalNavigationDrawerSheet 自带的 insets 处理（windowInsets=WindowInsets(0)
 * 只去掉水平多余边距，顶部状态栏间距仍保留）。
 */
@Composable
fun AppDrawerSheet(
    currentRoute: String?,
    onNavigate: (Destination) -> Unit,
) {
    val dimens = AppTheme.dimens
    val grouped = drawerItems().groupBy { it.group }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets(0),
        modifier = Modifier.fillMaxWidth(0.72f),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.spaceMd),
        ) {
            Spacer(Modifier.height(dimens.spaceLg))
            var firstGroup = true
            DrawerGroup.entries.forEach { group ->
                val items = grouped[group].orEmpty()
                if (items.isEmpty()) return@forEach
                if (!firstGroup) {
                    Spacer(Modifier.height(dimens.spaceXs))
                    HorizontalDivider()
                    Spacer(Modifier.height(dimens.spaceXs))
                }
                firstGroup = false
                items.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        selected = currentRoute == item.destination.route,
                        onClick = { onNavigate(item.destination) },
                        // 紧凑：去掉条目间垂直间距，默认形状已是全圆角胶囊
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(dimens.spaceLg))
        }
    }
}