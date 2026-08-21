package com.wxjxpp.musicplayer.core.source.online

import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.net.HttpClient
import org.json.JSONObject

/**
 * 酷我音乐平台适配。
 *
 * 搜索：`search.kuwo.cn/r.s`（客户端接口，返回 JSON，无需签名）
 * 歌词：`m.kuwo.cn/newh5/singles/songinfoandlrc`（H5 接口，返回逐行歌词数组）
 *
 * 注意 H5 歌词接口把翻译混在同一个数组里：同一时间点出现两条时，
 * 后一条是译文。这里按 LX 的做法拆成 lyric / tlyric 两份。
 */
internal class KuwoPlatform(http: HttpClient) : BasePlatform(http) {

    override val id: String = "kw"
    override val displayName: String = "酷我"

    override suspend fun search(keyword: String, page: Int, pageSize: Int): List<Song> {
        val url = "http://search.kuwo.cn/r.s?client=kt&all=${keyword.urlEncoded()}" +
            "&pn=${(page - 1).coerceAtLeast(0)}&rn=$pageSize&uid=794762570" +
            "&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music" +
            "&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        val response = http.get(url)
        if (!response.isSuccessful) return emptyList()
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return emptyList()
        val list = root.optJSONArray("abslist") ?: return emptyList()

        return list.objects().mapNotNull { item ->
            val songId = item.optString("MUSICRID").removePrefix("MUSIC_").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            // DURATION 是秒
            val duration = item.optString("DURATION").toLongOrNull()?.times(1000) ?: 0L
            val cover = item.optString("web_albumpic_short")
                .takeIf { it.isNotBlank() }
                ?.let { "https://img1.kuwo.cn/star/albumcover/" + it.replace("120/", "500/") }
            buildSong(
                songId = songId,
                title = item.optString("SONGNAME"),
                // 酷我用 & 分隔多歌手
                artist = item.optString("ARTIST").replace("&", " / "),
                albumTitle = item.optString("ALBUM"),
                durationMs = duration,
                coverUri = cover,
                albumId = item.optString("ALBUMID").takeIf { it.isNotBlank() },
                payload = item,
            )
        }
    }

    override suspend fun lyrics(songId: String, payload: String?): PlatformLyrics? {
        val response = http.get(
            "http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=$songId&httpsStatus=1",
            headers = mapOf("Referer" to "http://m.kuwo.cn/"),
        )
        if (!response.isSuccessful) return null
        val data = runCatching { JSONObject(response.body) }.getOrNull()
            ?.optJSONObject("data")
            ?: return null
        val lines = data.optJSONArray("lrclist") ?: return null
        if (lines.length() == 0) return null

        // 同一时间点的第二条视为译文
        val seen = mutableSetOf<String>()
        val main = StringBuilder()
        val translation = StringBuilder()
        lines.objects().forEach { line ->
            val seconds = line.optString("time").toDoubleOrNull() ?: return@forEach
            val text = line.optString("lineLyric").decodeEntities()
            if (text.isBlank()) return@forEach
            val stamp = formatLrcTime(seconds)
            val target = if (seen.add(stamp)) main else translation
            target.append(stamp).append(text).append('\n')
        }
        val lyric = main.toString().takeIf { it.isNotBlank() } ?: return null
        return PlatformLyrics(
            lyric = lyric,
            tlyric = translation.toString().takeIf { it.isNotBlank() },
        )
    }

    /** 秒（可能带小数）→ `[mm:ss.xx]`。 */
    private fun formatLrcTime(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong().coerceAtLeast(0)
        val minutes = totalMs / 60_000
        val secs = (totalMs % 60_000) / 1000
        val centis = (totalMs % 1000) / 10
        return "[%02d:%02d.%02d]".format(minutes, secs, centis)
    }
}