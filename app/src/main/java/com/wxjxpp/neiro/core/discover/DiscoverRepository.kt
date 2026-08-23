package com.wxjxpp.neiro.core.discover

import com.wxjxpp.neiro.core.model.Album
import com.wxjxpp.neiro.core.model.Artist
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.net.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/**
 * 发现页数据仓库。
 *
 * 数据来自网易云公开接口（无需登录）：
 * - 榜单：官方权威榜（热歌/新歌/飙升/原创），走 `/api/v3/playlist/detail`
 * - 猜你喜欢：以"最近播放的歌手/歌名"为种子走搜索接口
 *
 * 返回的歌曲全部带 Remote location（payload 为平台原始 JSON），
 * 播放统一交给用户导入的音源（含自动换源回退），本仓库不做取流。
 */
class DiscoverRepository(
    private val http: HttpClient,
    /** 关键词搜索委托：由在线搜索仓库提供（聚合或指定平台）。 */
    private val searchDelegate: suspend (keyword: String, page: Int, pageSize: Int) -> List<Song>,
) {

    data class Section(
        val id: String,
        val title: String,
        val subtitle: String,
        val songs: List<Song>,
    )

    data class ToplistRef(val id: String, val name: String, val updateFreq: String)

    /** 内置官方榜单。 */
    val toplists = listOf(
        ToplistRef("3778678", "热歌榜", "反映当前最热门的歌曲 · 每周四更新"),
        ToplistRef("3779629", "新歌榜", "最新发行歌曲 · 每天更新"),
        ToplistRef("19723756", "飙升榜", "热度上升最快的歌曲 · 每天更新"),
        ToplistRef("2884035", "原创榜", "独立音乐人作品 · 每周四更新"),
    )

    /**
     * 并发拉取发现页全部区块。
     *
     * [seedForGuess] 是"猜你喜欢"的搜索种子（如最近播放的歌手名），
     * 为空时跳过该区块。单个区块失败不影响其他区块。
     */
    suspend fun homeSections(
        songsPerSection: Int = 20,
        seedForGuess: String? = null,
    ): List<Section> = coroutineScope {
        val jobs = toplists.map { ref ->
            async {
                val songs = runCatching { toplistSongs(ref.id, songsPerSection) }
                    .getOrDefault(emptyList())
                Section(ref.id, ref.name, ref.updateFreq, songs)
            }
        }.toMutableList()

        if (!seedForGuess.isNullOrBlank()) {
            jobs += async {
                val songs = runCatching { guessYouLike(listOf(seedForGuess), songsPerSection) }
                    .getOrDefault(emptyList())
                Section("guess", "猜你喜欢", "根据你的最近播放推荐", songs)
            }
        }

        jobs.mapNotNull { job ->
            val section = job.await()
            section.takeIf { it.songs.isNotEmpty() }
        }
    }

    /** 拉取单个榜单的完整曲目。 */
    suspend fun toplistSongs(listId: String, limit: Int = 50): List<Song> {
        val response = http.get(
            "https://music.163.com/api/v3/playlist/detail?id=$listId&n=$limit",
            headers = mapOf("Referer" to "https://music.163.com"),
        )
        if (!response.isSuccessful) return emptyList()
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return emptyList()
        val playlist = root.optJSONObject("playlist") ?: return emptyList()
        val tracks = playlist.optJSONArray("tracks") ?: return emptyList()
        return buildTracks(tracks, limit)
    }

    /** 猜你喜欢：依次尝试种子关键词，命中即返回打散的结果。 */
    suspend fun guessYouLike(seedQueries: List<String>, limit: Int = 20): List<Song> {
        seedQueries.filter { it.isNotBlank() }.forEach { query ->
            val songs = runCatching { searchDelegate(query.trim(), 1, limit) }
                .getOrDefault(emptyList())
            if (songs.isNotEmpty()) return songs.shuffled().take(limit)
        }
        return emptyList()
    }

    /** 把平台 track JSON 数组映射成 Song 列表（payload 原样保留供脚本取流）。 */
    private fun buildTracks(tracks: JSONArray, limit: Int): List<Song> =
        (0 until minOf(tracks.length(), limit)).mapNotNull { i ->
            val info = tracks.optJSONObject(i) ?: return@mapNotNull null
            parseTrack(info)
        }

    private fun parseTrack(info: JSONObject): Song? {
        val songId = info.optLong("id").takeIf { it > 0 }?.toString() ?: return null
        val title = info.optString("name").ifBlank { return null }
        val album = info.optJSONObject("al")
        return Song(
            id = "wy:$songId",
            title = title,
            artists = info.optJSONArray("ar")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONObject(i)?.let { o ->
                            Artist(id = o.optString("id"), name = o.optString("name"))
                        }
                    }
                } ?: emptyList(),
            album = Album(
                id = "album:${album?.optLong("id") ?: title}",
                title = album?.optString("name") ?: "未知专辑",
                artistName = info.optJSONArray("ar").joinNames().takeIf { it.isNotBlank() },
                coverUri = album?.optString("picUrl")?.takeIf { it.isNotBlank() },
                year = album?.optLong("publishTime")?.takeIf { it > 0 }
                    ?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).year },
            ),
            durationMs = info.optLong("dt"),
            coverUri = album?.optString("picUrl")?.takeIf { it.isNotBlank() },
            location = MediaLocation.Remote(
                sourceId = "wy",
                songId = songId,
                payload = info.toString(),
            ),
            metadataComplete = true,
            tags = listOf("网易云"),
        )
    }

    /** 把 ["name":"xx"] 数组拼成 "xx / yy"；接收者可空（字段缺失时返回空串）。 */
    private fun JSONArray?.joinNames(key: String = "name"): String {
        if (this == null) return ""
        return (0 until length()).mapNotNull { optJSONObject(it)?.optString(key)?.takeIf(String::isNotBlank) }
            .joinToString(" / ")
    }
}