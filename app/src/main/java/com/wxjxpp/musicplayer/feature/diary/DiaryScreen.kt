package com.wxjxpp.musicplayer.feature.diary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wxjxpp.musicplayer.core.model.HeatmapDay
import com.wxjxpp.musicplayer.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 听歌热力图（听歌日记）。
 *
 * GitHub 风格的日历热力图，展示最近一年：
 * - 色块五级（浅→深）按当天播放歌曲数划分：
 *   0 首 / 1-4 / 5-9 / 10-19 / 20-39 / 40+
 * - 色相取自主题 primary（莫奈取色由 Material 动态色提供）
 * - 点击色块看当天详情：播放数 / 启动次数 / 收听时长 / 高频标签
 */
@Composable
fun DiaryScreen(
    days: List<HeatmapDay>,
    isLoading: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    // 把数据转成按天索引的 map，并补齐整年的空白天
    val dayMap = remember(days) { days.associateBy { it.dateMs } }
    val calendarDays = remember(dayMap) { buildYearDays() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Text(
            text = "听歌热力图",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        )
        Text(
            text = if (isLoading) "加载中…" else "最近 365 天 · 共 ${days.sumOf { it.playCount }} 次播放",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
        )
        Spacer(Modifier.height(dimens.spaceMd))
        // 热力图网格：横向可滚动（53 周 × 7 天）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = dimens.spaceLg),
            verticalAlignment = Alignment.Top,
        ) {
            val cellSize = 14.dp
            val cellGap = 3.dp
            // 按周分列
            val weeks = calendarDays.chunked(7)
            weeks.forEach { week ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(cellGap),
                    modifier = Modifier.padding(end = cellGap),
                ) {
                    week.forEach { day ->
                        val data = dayMap[day]
                        val level = data?.level ?: 0
                        val color = heatmapColor(level)
                        Surface(
                            color = color,
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.size(cellSize),
                        ) {}
                    }
                    // 补齐最后一周不足 7 天的空位
                    if (week.size < 7) {
                        repeat(7 - week.size) {
                            Spacer(Modifier.size(cellSize))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(dimens.spaceLg))
        // 图例：少 → 多
        Row(
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "少",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            repeat(6) { level ->
                androidx.compose.material3.Surface(
                    color = heatmapColor(level),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(14.dp),
                ) {}
            }
            Text(
                "多",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Spacer(Modifier.height(dimens.spaceLg))
        // 等级说明
        Text(
            text = "等级：1-4 首一级 · 5-9 首二级 · 10-19 首三级 · 20-39 首四级 · 40+ 首五级",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
        )
    }
}

/** 生成从今天倒推 365 天、按周对齐的日期列表（每周从周一开始）。 */
private fun buildYearDays(): List<Long> {
    val calendar = Calendar.getInstance()
    val today = calendar.timeInMillis
    calendar.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    // 对齐到本周的周一
    val dow = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 周一=0
    calendar.add(Calendar.DAY_OF_YEAR, -dow)
    val end = calendar.timeInMillis
    // 从 364 天前的周一开始
    calendar.add(Calendar.DAY_OF_YEAR, -(52 * 7))
    val start = calendar.timeInMillis
    val days = mutableListOf<Long>()
    var cursor = start
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