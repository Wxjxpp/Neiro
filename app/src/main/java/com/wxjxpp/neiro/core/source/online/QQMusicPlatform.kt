package com.wxjxpp.neiro.core.source.online

import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.net.HttpClient
import org.json.JSONObject

/**
 * QQ 音乐平台适配。
 *
 * 搜索：`c.y.qq.com/soso/fcgi-bin/search_for_qq_cp`（`new_json=1`，无需签名）
 * 歌词：`c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg`，
 *       用 `nobase64=1` 直接拿明文，省掉一次 base64 解码。
 *
 * 两个必须注意的点：
 * - 歌词接口强校验 Referer，缺了会返回 retcode != 0
 * - 歌词要用 `songmid`（字符串），不是 `songid`（数字）
 */
internal class QQMusicPlatform(http: HttpClient) : BasePlatform(http) {

    override val id: String = "tx"
    override val displayName: String = "QQ音乐"

    private val referer = mapOf("Referer" to "https://y.qq.com/portal/player.html")

    override suspend fun search(keyword: String, page: Int, pageSize: Int): List<Song> {
        val url = "https://c.y.qq.com/soso/fcgi-bin/search_for_qq_cp?ct=24&qqmusic_ver=1298" +
            "&new_json=1&remoteplace=txt.yqq.song&t=0&aggr=1&cr=1&catZhida=1&lossless=0" +
            "&flag_qc=0&p=$page&n=$pageSize&w=${keyword.urlEncoded()}" +
            "&format=json&inCharset=utf8&outCharset=utf-8&platform=yqq.json"
        val response = http.get(url, referer)
        if (!response.isSuccessful) return emptyList()
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return emptyList()
        if (root.optInt("code", -1) != 0) return emptyList()
        val list = root.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
            ?: return emptyList()

        return list.objects().mapNotNull { item ->
            // new_json=1 时字段是嵌套结构；兼容旧扁平结构
            val songMid = item.optString("mid").ifBlank { item.optString("songmid") }
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val album = item.optJSONObject("album")
            val albumMid = album?.optString("mid")?.ifBlank { item.optString("albummid") }
                ?: item.optString("albummid")
            val cover = albumMid.takeIf { it.isNotBlank() }
                ?.let { "https://y.gtimg.cn/music/photo_new/T002R500x500M000$it.jpg" }
            buildSong(
                songId = songMid,
                title = item.optString("title").ifBlank { item.optString("songname") },
                artist = item.optJSONArray("singer").joinNames(),
                albumTitle = album?.optString("title")?.ifBlank { item.optString("albumname") }
                    ?: item.optString("albumname"),
                durationMs = item.optLong("interval") * 1000,
                coverUri = cover,
                albumId = albumMid.takeIf { it.isNotBlank() },
                payload = item,
            )
        }
    }

    override suspend fun lyrics(songId: String, payload: String?): PlatformLyrics? {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songId" +
            "&format=json&nobase64=1&g_tk=5381&loginUin=0&hostUin=0" +
            "&inCharset=utf8&outCharset=utf-8&platform=yqq"
        val response = http.get(url, referer)
        if (!response.isSuccessful) return null
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        if (root.optInt("retcode", -1) != 0) return null
        val lyric = root.optString("lyric").takeIf { it.isNotBlank() } ?: return null
        return PlatformLyrics(
            lyric = lyric,
            tlyric = root.optString("trans").takeIf { it.isNotBlank() },
        )
    }
}
