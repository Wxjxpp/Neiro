package com.wxjxpp.musicplayer.core.search

import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.source.OnlineMusicSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 在线聚合搜索。
 *
 * 两种模式：
 * - 指定平台：只搜一个平台，翻页有意义
 * - 全部平台（[ALL]）：并发搜索后按平台交错合并，让首屏能同时看到多家结果
 *
 * 单个平台失败不影响其它平台：各自 runCatching，返回空列表即可。
 */
class OnlineSearchRepository(
    private val sources: List<OnlineMusicSource>,
) {

    /** 可选平台列表，供 UI 渲染筛选条。 */
    val platforms: List<PlatformOption> = listOf(PlatformOption(ALL, "全部")) +
        sources.map { PlatformOption(it.id, it.displayName) }

    data class PlatformOption(val id: String, val displayName: String)

    /** 搜索结果与失败信息。UI 需要区分"没搜到"和"全都失败了"。 */
    data class Result(
        val songs: List<Song> = emptyList(),
        val failedPlatforms: List<String> = emptyList(),
    )

    suspend fun search(
        keyword: String,
        platformId: String = ALL,
        page: Int = 1,
        pageSize: Int = 30,
    ): Result {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return Result()

        val targets = if (platformId == ALL) sources else sources.filter { it.id == platformId }
        if (targets.isEmpty()) return Result()

        return coroutineScope {
            val results = targets.map { source ->
                async {
                    val songs = runCatching { source.search(trimmed, page, pageSize) }
                        .getOrNull()
                    source to songs
                }
            }.map { it.await() }

            val failed = results.filter { it.second == null }.map { it.first.displayName }
            val lists = results.mapNotNull { it.second }.filter { it.isNotEmpty() }
            Result(songs = interleave(lists), failedPlatforms = failed)
        }
    }

    /** 轮转合并多个平台的结果，避免某个平台把首屏占满。 */
    private fun interleave(lists: List<List<Song>>): List<Song> {
        if (lists.size <= 1) return lists.flatten()
        val result = mutableListOf<Song>()
        val seen = mutableSetOf<String>()
        val maxSize = lists.maxOf { it.size }
        for (i in 0 until maxSize) {
            for (list in lists) {
                val song = list.getOrNull(i) ?: continue
                if (seen.add(song.id)) result += song
            }
        }
        return result
    }

    companion object {
        const val ALL = "all"
    }
}