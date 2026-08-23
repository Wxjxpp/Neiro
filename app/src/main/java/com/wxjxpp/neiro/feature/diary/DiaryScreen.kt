package com.wxjxpp.neiro.feature.diary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.model.HeatmapDay
import com.wxjxpp.neiro.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 听歌热力图（听歌日记）。
 *
 * - GitHub 风格日历色块，五级深浅按当天播放数划分：
 *   0 首 / 1-4 / 5-9 / 10-19 / 20-39 / 40+
 * - 支持切换「最近一年 / 最近一个月」跨度；月份标注直接叠在网格上方对应位置
 * - 默认滚动到今天（最右端），未来日期不渲染空格子
 * - 点击色块查看当天详情：播放数 / 启动次数 / 收听时长 / 高频标签
 */
@Composable
fun DiaryScreen(
    days: List<HeatmapDay>,
    isLoading: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
) {
    val dimens = AppTheme.dimens
    var rangeMonths by remember { mutableStateOf(12) }
    val dayMap = remember(days) { days.associateBy { it.dateMs } }
    // 网格数据：按范围生成，只保留 <= 今天 的日期（不再渲染未来空格子）
    val calendarDays = remember(rangeMonths) { buildDays(rangeMonths) }
    var selectedDay by remember(days, rangeMonths) { mutableStateOf<HeatmapDay?>(null) }
    val dateFormat = remember { SimpleDateFormat("M月d日", Locale.getDefault()) }
    val fullFormat = remember { SimpleDateFormat("yyyy年M月d日 EEEE", Locale.getDefault()) }

    // 色块尺寸固定：月份标注按同一套尺寸计算偏移
    val cellSize = 14.dp
    val cellGap = 3.dp
    val weekColumnWidth = cellSize + cellGap
    val density = LocalDensity.current

    // 热力图横向滚动容器：默认滚到最右端（今天）
    val heatScrollState = rememberScrollState()
    val gridMaxIndex = (calendarDays.size / 7).coerceAtLeast(0)
    LaunchedEffect(rangeMonths, gridMaxIndex) {
        // 等横向布局完成（maxValue>0）再滚到最右，否则首次 scrollTo(0) 无效
        snapshotFlow { heatScrollState.maxValue }.first { it > 0 }
        val targetPx = with(density) { (gridMaxIndex * weekColumnWidth.toPx()).toInt() }
        heatScrollState.scrollTo(targetPx)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(contentPadding),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
            ) {
                androidx.compose.material3.IconButton(onClick = onOpenDrawer) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Rounded.Menu,
                        contentDescription = "打开导航",
                    )
                }
                Text(
                    text = "听歌日记",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                SingleChoiceSegmentedButtonRow {
                    listOf(1 to "一个月", 12 to "一年").forEachIndexed { index, (months, label) ->
                        SegmentedButton(
                            selected = rangeMonths == months,
                            onClick = { rangeMonths = months },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
        item {
            // 年度概览
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceLg),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCard("总播放", "${days.sumOf { it.playCount }}")
                StatCard("总时长", "${days.sumOf { it.listenedMs } / 60000} 分")
                StatCard("启动次数", "${days.sumOf { it.launchCount }}")
                StatCard("活跃天数", days.count { it.playCount > 0 }.toString())
            }
        }
        item {
            Text(
                text = when {
                    isLoading -> "加载中…"
                    rangeMonths == 1 -> "最近一个月"
                    else -> "最近一年"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
            )
        }
        item {
            // 月份标注行：与下方网格共用同一个横向滚动状态，偏移量逐像素对齐
            val monthMarks = remember(calendarDays) { monthLabelOffsets(calendarDays) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceLg),
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(heatScrollState),
                ) {
                    Spacer(Modifier.width(weekColumnWidth))
                    monthMarks.forEachIndexed { index, label ->
                        if (label != null) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(weekColumnWidth * 4),
                            )
                        } else {
                            Spacer(Modifier.width(weekColumnWidth))
                        }
                        if (index == monthMarks.lastIndex) Spacer(Modifier.width(cellGap))
                    }
                }
            }
        }
        item {
            // 热力图网格：横向可滚动（每周一列 × 7 天）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(heatScrollState)
                    .padding(horizontal = dimens.spaceLg),
                verticalAlignment = Alignment.Top,
            ) {
                calendarDays.chunked(7).forEach { week ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(cellGap),
                        modifier = Modifier.padding(end = cellGap),
                    ) {
                        week.forEach { day ->
                            val data = dayMap[day]
                            Surface(
                                color = heatmapColor(data?.level ?: 0),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .size(cellSize)
                                    .clickable { selectedDay = data },
                            ) {}
                        }
                        if (week.size < 7) repeat(7 - week.size) { Spacer(Modifier.size(cellSize)) }
                    }
                }
            }
        }
        item {
            // 图例
            Row(
                modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                repeat(6) { level ->
                    Surface(
                        color = heatmapColor(level),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    ) {}
                }
                Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    "1-4/5-9/10-19/20-39/40+",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 当天详情卡片
        selectedDay?.let { day ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = fullFormat.format(Date(day.dateMs)),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "关闭",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { selectedDay = null },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            DayDetail("播放歌曲", "${day.playCount} 首")
                            DayDetail("应用启动", "${day.launchCount} 次")
                            DayDetail("收听时长", formatMinutes(day.listenedMs))
                        }
                        if (day.topTags.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("常听类型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                day.topTags.entries.take(5).forEach { (tag, count) ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(50),
                                    ) {
                                        Text(
                                            "$tag ×$count",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 最近听歌日期明细
        item {
            Text(
                text = "最近记录",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
            )
        }
        val recent = days.sortedByDescending { it.dateMs }.take(30)
        items(recent.size, key = { recent[it].dateMs }) { index ->
            val day = recent[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedDay = day }
                    .padding(horizontal = dimens.spaceLg, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = heatmapColor(day.level),
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier.size(18.dp),
                ) {}
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(dateFormat.format(Date(day.dateMs)), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${day.playCount} 首 · ${formatMinutes(day.listenedMs)} · 启动 ${day.launchCount} 次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (day.topTags.isNotEmpty()) {
                    Text(
                        day.topTags.keys.take(2).joinToString(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(dimens.spaceXl)) }
    }
}

/**
 * 生成月份标注序列：与 [calendarDays] 的周列一一对应。
 *
 * 返回列表长度 = 周列数；某周的第一天开启了新月份，则该列标注「M月」。
 */
private fun monthLabelOffsets(calendarDays: List<Long>): List<String?> {
    val cal = Calendar.getInstance()
    val labels = mutableListOf<String?>()
    var lastMonth = -1
    calendarDays.chunked(7).forEach { week ->
        if (week.isEmpty()) {
            labels += null
            return@forEach
        }
        cal.timeInMillis = week.first()
        val month = cal.get(Calendar.MONTH)
        if (month != lastMonth) {
            labels += "${month + 1}月"
            lastMonth = month
        } else {
            labels += null
        }
    }
    return labels
}

@Composable
private fun StatCard(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayDetail(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatMinutes(ms: Long): String {
    val minutes = ms / 60000
    return when {
        minutes >= 60 -> "${minutes / 60}时${minutes % 60}分"
        else -> "${minutes}分"
    }
}

/**
 * 生成从今天倒推 [months] 个月的日期列表（按周对齐，每周从周一开始）。
 *
 * 只包含 <= 今天 的日期：未来日期不渲染，避免"上半截全是空格子"。
 */
private fun buildDays(months: Int): List<Long> {
    val calendar = Calendar.getInstance()
    val today = calendar.timeInMillis
    calendar.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    // 范围终点：本周的周一（含今天所在周，保证今天的格子存在）
    val dow = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 周一=0
    calendar.add(Calendar.DAY_OF_YEAR, -dow)
    val end = calendar.timeInMillis
    // 范围起点：倒推 months 个月，再对齐到该周的周一
    calendar.timeInMillis = end
    calendar.add(Calendar.MONTH, -months)
    val dowStart = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    calendar.add(Calendar.DAY_OF_YEAR, -dowStart)
    val days = mutableListOf<Long>()
    var cursor = calendar.timeInMillis
    while (cursor <= end && cursor <= today) {
        days += cursor
        calendar.timeInMillis = cursor
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        cursor = calendar.timeInMillis
    }
    return days
}

/** 热力图颜色：0-5 级，基于主题 primary 的透明度梯度（跟随莫奈动态取色）。 */
@Composable
private fun heatmapColor(level: Int): Color {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    return when (level) {
        0 -> surfaceVariant
        1 -> primary.copy(alpha = 0.25f)
        2 -> primary.copy(alpha = 0.45f)
        3 -> primary.copy(alpha = 0.65f)
        4 -> primary.copy(alpha = 0.85f)
        else -> primary
    }
}