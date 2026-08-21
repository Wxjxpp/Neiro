package com.wxjxpp.musicplayer.core.source.online

import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.net.HttpClient
import org.json.JSONObject

/**
 * 咪咕音乐平台适配。
 *
 * 搜索：`app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do`
 *       （相比 jadeite 的 v3 接口不需要 MD5 签名，字段一致）
 * 歌词：搜索结果里直接给了 `lyricUrl`（纯 LRC 文本）与 `mrcurl`（逐字，需解密），
 *       这里取 lyricUrl，逐字留给自定义音源脚本。
 *
 * 返回结构有一层数组嵌套：`songResultData.resultList` 是 `Array<Array<Song>>`。
 */
internal class MiguPlatform(http: HttpClient) : BasePlatform(http) {

    override val id: String = "mg"
    override val displayName: String = "咪咕"

    private val headers = mapOf(
        "User-Agent" to "okhttp/3.9.1",
        "channel" to "0146921",
        "Referer" to "https://app.c.nf.migu.cn/",
    )

    override suspend fun search(keyword: String, page: Int, pageSize: Int): List<Song> {
        val url = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do" +
            "?isCopyright=1&isCorrect=1&pageNo=$page&pageSize=$pageSize" +
            "&searchSwitch=%7B%22song%22%3A1%7D&sort=0&text=${keyword.urlEncoded()}"
        val response = http.get(url, headers)
        if (!response.isSuccessful) return emptyList()
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return emptyList()
        if (root.optString("code") != "000000") return emptyList()
        val groups = root.optJSONObject("songResultData")?.optJSONArray("resultList")
            ?: return emptyList()

        val seen = mutableSetOf<String>()
        val result = mutableListOf<Song>()
        (0 until groups.length()).forEach { i ->
            val group = groups.optJSONArray(i) ?: return@forEach
            group.objects().forEach { item ->
                val songId = item.optString("songId").takeIf { it.isNotBlank() } ?: return@forEach
                val copyrightId = item.optString("copyrightId")
                if (!seen.add(copyrightId.ifBlank { songId })) return@forEach
                var cover = item.optString("img3").ifBlank { item.optString("img2") }
                    .ifBlank { item.optString("img1") }
                if (cover.isNotBlank() && !cover.startsWith("http")) {
                    cover = "http://d.musicapp.migu.cn$cover"
                }
                result += buildSong(
                    songId = songId,
                    title = item.optString("name"),
                    artist = item.optJSONArray("singerList").joinNames()
                        .ifBlank { item.optJSONArray("singers").joinNames() },
                    albumTitle = item.optString("album"),
                    // duration 是秒
                    durationMs = item.optString("duration").toLongOrNull()?.times(1000) ?: 0L,
                    coverUri = cover.takeIf { it.isNotBlank() },
                    albumId = item.optString("albumId").takeIf { it.isNotBlank() },
                    payload = item,
                )
            }
        }
        return result
    }

    override suspend fun lyrics(songId: String, payload: String?): PlatformLyrics? {
        val lyricUrl = payload?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.optString("lyricUrl")
            ?.takeIf { it.startsWith("http") }
            ?: return null
        val response = http.get(lyricUrl, headers)
        if (!response.isSuccessful) return null
        return response.body.takeIf { it.isNotBlank() }?.let { PlatformLyrics(lyric = it) }
    }
}

/**
 * 默认在线平台集合。
 *
 * 顺序决定聚合搜索的展示顺序，也决定本地歌曲反查歌词的尝试顺序。
 * 每个平台都只用公开接口，不含任何取流逻辑（网易云的 weapi 取流除外，
 * 它是官方 Web 播放器同款接口，见 [NeteasePlatform.streamUrl]）。
 */
internal fun defaultOnlinePlatforms(
    http: HttpClient,
    neteaseCookieProvider: () -> String = { "" },
): List<OnlinePlatform> = listOf(
    KuwoPlatform(http),
    QQMusicPlatform(http),
    NeteasePlatform(http, neteaseCookieProvider),
    KugouPlatform(http),
    MiguPlatform(http),
)