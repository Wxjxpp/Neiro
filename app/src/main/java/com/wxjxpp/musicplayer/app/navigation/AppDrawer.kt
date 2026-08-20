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
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.wxjxpp.musicplayer.ui.theme.AppTheme
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
    add(DrawerItem(Destination.Home, "歌曲", Icons.Filled.MusicNote, DrawerGroup.Library))
    add(DrawerItem(Destination.Library, "本地曲库", Icons.Filled.LibraryMusic, DrawerGroup.Library))
    add(DrawerItem(Destination.Playlists, "歌单", Icons.Filled.QueueMusic, DrawerGroup.Library))
    add(DrawerItem(Destination.Diary, "听歌日记", Icons.Filled.Book, DrawerGroup.Personal))
    add(DrawerItem(Destination.Together, "一起听", Icons.Filled.Groups, DrawerGroup.Personal))
    if (isAnnualReportAvailable(today)) {
        add(DrawerItem(Destination.Report, "年度报告", Icons.Filled.Insights, DrawerGroup.Personal))
    }
    add(DrawerItem(Destination.Settings, "设置", Icons.Filled.Settings, DrawerGroup.System))
}

@Composable
fun AppDrawerSheet(
    currentRoute: String?,
    onNavigate: (Destination) -> Unit,
) {
    val dimens = AppTheme.dimens
    val grouped = drawerItems().groupBy { it.group }

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.spaceMd),
        ) {
            Spacer(Modifier.height(dimens.spaceXl))
            DrawerGroup.entries.forEachIndexed { index, group ->
                val items = grouped[group].orEmpty()
                if (items.isEmpty()) return@forEachIndexed
                if (index > 0) {
                    Spacer(Modifier.height(dimens.spaceSm))
                    HorizontalDivider()
                    Spacer(Modifier.height(dimens.spaceSm))
                }
                items.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        selected = currentRoute == item.destination.route,
                        onClick = { onNavigate(item.destination) },
                        modifier = Modifier.padding(vertical = dimens.spaceXs),
                    )
                }
            }
            Spacer(Modifier.height(dimens.spaceLg))
        }
    }
}