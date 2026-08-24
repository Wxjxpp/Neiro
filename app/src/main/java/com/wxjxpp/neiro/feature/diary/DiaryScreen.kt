package com.wxjxpp.neiro.feature.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.model.HeatmapDay
import com.wxjxpp.neiro.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 听歌日记。
 *
 * - **月视图**：独立日历样式（周一~周日 7 列网格），左右箭头翻月，当月未过完的日期不显示；
 * - **年视图**：GitHub 热力图，月份标注与网格逐列对齐（从当年 1 月排到 12 月，不再乱序）；
 * - 点击色块查看当天详情：播放数 / 启动次数 / 收听时长 / 高频标签。
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
    var rangeMonths by remember { mutableStateOf(1) }
    val dayMap = remember(days) { days.associateBy { it.dateMs } }
    var selectedDay by remember { mutableStateOf<HeatmapDay?>(null) }
    val fullFormat = remember { SimpleDateFormat("yyyy年M月d日 EEEE", Locale.getDefault()) }

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
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Rounded.Menu, contentDescription = "打开导航")
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
                            onClick = { rangeMonths = months; selectedDay = null },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
        item {
            // 年度概览：随所选范围联动统计
            val scopeDays = if (rangeMonths == 1) {
                // 单月：只统计当前查看的那个月
                val cal = Calendar.getInstance()
                val ym = cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
                days.filter { d ->
                    cal.timeInMillis = d.dateMs
                    (cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)) == ym
                }
            } else days
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceLg),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCard("总播放", "${scopeDays.sumOf { it.playCount }}")
                StatCard("总时长", "${scopeDays.sumOf { it.listenedMs } / 60000} 分")
                StatCard("启动次数", "${scopeDays.sumOf { it.launchCount }}")
                StatCard("活跃天数", scopeDays.count { it.playCount > 0 }.toString())
            }
        }
        if (rangeMonths == 1) {
            item {
                MonthCalendarView(dayMap = dayMap, onDayClick = { selectedDay = it })
            }
        } else {
            item {
                YearHeatmapView(dayMap = dayMap, onDayClick = { selectedDay = it })
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
                if (isLoading) {
                    Text(
                        "加载中…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 当天详情卡片
        selectedDay?.let { day ->
            item(key = "detail") {
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
        items(recent, key = { it.dateMs }) { day ->
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
                    Text(dayLabel.format(Date(day.dateMs)), style = MaterialTheme.typography.bodyMedium)
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

private val dayLabel = SimpleDateFormat("M月d日", Locale.getDefault())

/**
 * 月视图：真正的单月日历。
 *
 * - 标题「2025年8月」+ 左右翻月箭头 + 回到本月按钮；
 * - 周一~周日固定表头，7 列网格，按周一对齐补空位；
 * - 未来日期不渲染；今天有描边高亮。
 */
@Composable
private fun MonthCalendarView(
    dayMap: Map<Long, HeatmapDay>,
    onDayClick: (HeatmapDay?) -> Unit,
) {
    var offset by remember { mutableStateOf(0) } // 0=本月，-1=上月…
    val base = Calendar.getInstance()
    fun monthCal(offset: Int): Calendar = (base.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, offset)
    }
    val titleFormat = remember { SimpleDateFormat("yyyy年M月", Locale.getDefault()) }
    val cal = monthCal(offset)
    val yearMonth = remember(offset) { titleFormat.format(cal.time) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.dimens.spaceLg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { offset -= 1 }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                text = yearMonth,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { if (offset < 0) offset += 1 }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下个月")
            }
            if (offset != 0) {
                TextButton(onClick = { offset = 0 }) { Text("本月") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
                // 直接按当前 offset 计算格子（不 remember 缓存 Calendar 实例，避免翻月读到旧对象）
        val cells = buildMonthCells(cal)
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                week.forEach { cell ->
                    Box(
                        contentAlignment = Alignment.Center,
                        // weight(1f) 保证每列等宽等高，末行不再被拉伸成大格子
                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                    ) {
                        when (cell) {
                            null -> {}
                            else -> {
                                val data = dayMap[cell]
                                val isToday = cell == todayStart()
                                val isFuture = cell > todayStart()
                                if (!isFuture) {
                                    Surface(
                                        color = heatmapColor(data?.level ?: 0),
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (isToday) {
                                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                        } else null,
                                        modifier = Modifier.fillMaxSize().clickable { onDayClick(data) },
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = dayNumber(cell),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isToday) FontWeight.Bold else null,
                                            )
                                        }
                                    }
                                } else {
                                    // 未来日期：灰底 + 「这个月还没过呢」提示
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    ) {
                                        if (cell == monthFirstFutureDay(cells)) {
                                            Text(
                                                text = "这个月还没过呢",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 年视图：GitHub 风格热力图（月份标注已修复为严格顺序）。 */
@Composable
private fun YearHeatmapView(
    dayMap: Map<Long, HeatmapDay>,
    onDayClick: (HeatmapDay?) -> Unit,
) {
    val cellSize = 14.dp
    val cellGap = 3.dp
    val weekColumnWidth = cellSize + cellGap
    // 网格数据：从今年 1 月 1 日所在周的周一开始 → 今天所在周（含未来截断）
    val weeks = remember(dayMap.size) { buildYearWeeks() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val heatScrollState = rememberScrollState()
    val gridMaxIndex = (weeks.size - 1).coerceAtLeast(0)
    androidx.compose.runtime.LaunchedEffect(gridMaxIndex) {
        snapshotFlowMax(heatScrollState) { max ->
            val targetPx = with(density) { (gridMaxIndex * weekColumnWidth.toPx()).toInt() }
            heatScrollState.scrollTo(targetPx.coerceAtMost(max))
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        // 月份标注行：每个非空标注占满该月的真实周列数，保证与网格对齐、顺序正确
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(heatScrollState)
                .padding(horizontal = AppTheme.dimens.spaceLg),
        ) {
            Spacer(Modifier.width(weekColumnWidth))
            weeks.forEachIndexed { weekIndex, week ->
                val first = week.firstOrNull() ?: return@forEachIndexed
                if (isMonthFirstWeek(week, weeks, weekIndex)) {
                    val cal = Calendar.getInstance().apply { timeInMillis = first }
                    val month = cal.get(Calendar.MONTH)
                    // 标注宽度 = 该月占用的真实周列数 × 列宽，与网格逐像素对齐
                    val span = weeksInMonth(weeks, month, weekIndex)
                    Text(
                        text = "${month + 1}月",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(weekColumnWidth * span),
                    )
                }
            }
        }
        // 热力图网格
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(heatScrollState)
                .padding(horizontal = AppTheme.dimens.spaceLg),
            verticalAlignment = Alignment.Top,
        ) {
            weeks.forEach { week ->
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
                                .clickable(enabled = data != null) { onDayClick(data) },
                        ) {}
                    }
                    if (week.size < 7) repeat(7 - week.size) { Spacer(Modifier.size(cellSize)) }
                }
            }
        }
    }
}

/** 滚动到最右端的小工具：等横向布局完成再滚。 */
private suspend fun snapshotFlowMax(
    state: androidx.compose.foundation.ScrollState,
    block: suspend (Int) -> Unit,
) {
    androidx.compose.runtime.snapshotFlow { state.maxValue }.first { it > 0 }
    block(state.maxValue)
}

/** 时间戳 → 日号文本（月视图格子里的数字）。 */
private fun dayNumber(ms: Long): String =
    Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_MONTH).toString()

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

/** 今天 0 点的时间戳。 */
internal fun todayStart(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** 该月日历格子（含前置补位 null）；未来日期不包含。 */
internal fun buildMonthCells(monthCal: Calendar): List<Long?> {
    val today = todayStart()
    val cal = (monthCal.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 周一=0
    val cells = mutableListOf<Long?>()
    repeat(dow) { cells += null }
    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    for (d in 1..maxDay) {
        cal.set(Calendar.DAY_OF_MONTH, d)
        cells += cal.timeInMillis
    }
    return cells
}

/** 未来日期区的第一天（用于放「这个月还没过呢」提示，只显示一次）。 */
internal fun monthFirstFutureDay(cells: List<Long?>): Long? =
    cells.filterNotNull().firstOrNull { it > todayStart() }

/** 年视图周列：今年 1 月所在周的周一开始 → 今天所在周。 */
internal fun buildYearWeeks(): List<List<Long>> {
    val today = todayStart()
    val cal = Calendar.getInstance().apply {
        timeInMillis = today
        set(Calendar.DAY_OF_YEAR, 1) // 今年 1 月 1 日
    }
    // 对齐到那一周的周一
    val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    cal.add(Calendar.DAY_OF_YEAR, -dow)
    val start = cal.timeInMillis
    val weeks = mutableListOf<List<Long>>()
    var cursor = start
    while (cursor <= today) {
        val week = mutableListOf<Long>()
        for (i in 0 until 7) {
            val c = Calendar.getInstance().apply { timeInMillis = cursor; add(Calendar.DAY_OF_YEAR, i) }
            if (c.timeInMillis <= today) week += c.timeInMillis
        }
        weeks += week
        val next = Calendar.getInstance().apply { timeInMillis = cursor; add(Calendar.DAY_OF_YEAR, 7) }
        cursor = next.timeInMillis
    }
    return weeks
}

/** 这一周是否是某个月在网格中的第一周（用于放月份标注）。 */
private fun isMonthFirstWeek(week: List<Long>, all: List<List<Long>>, index: Int): Boolean {
    if (week.isEmpty()) return false
    if (index == 0) return true
    val prevFirst = all.getOrNull(index - 1)?.firstOrNull() ?: return true
    val cal = Calendar.getInstance()
    cal.timeInMillis = prevFirst
    val prevMonth = cal.get(Calendar.MONTH)
    cal.timeInMillis = week.first()
    return cal.get(Calendar.MONTH) != prevMonth
}

/** 从 index 开始数连续同月周列的数量（作为月份标注宽度）。 */
private fun weeksInMonth(all: List<List<Long>>, month: Int, index: Int): Int {
    val cal = Calendar.getInstance()
    var count = 0
    for (i in index until all.size) {
        val first = all[i].firstOrNull() ?: break
        cal.timeInMillis = first
        if (cal.get(Calendar.MONTH) != month) break
        count++
    }
    return count.coerceAtLeast(1)
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