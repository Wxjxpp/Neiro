package com.wxjxpp.neiro.core.source.online

import android.util.Base64
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.net.HttpClient
import org.json.JSONObject

/**
 * 酷狗音乐平台适配。
 *
 * 搜索：`songsearch.kugou.com/song_search_v2`
 * 歌词：两步走 —— `lyrics.kugou.com/search` 拿 id + accesskey，
 *       再用 `lyrics.kugou.com/download` 下载，内容是 base64。
 *
 * 酷狗歌词接口需要 `hash` 与时长参与匹配，因此搜索时必须把
 * FileHash / Duration 一起存进 payload。
 */
internal class KugouPlatform(http: HttpClient) : BasePlatform(http) {

    override val id: String = "kg"
    override val displayName: String = "酷狗"

    private val lyricHeaders = mapOf(
        "KG-RC" to "1",
        "KG-THash" to "expand_search_manager.cpp:852736169:451",
        "User-Agent" to "KuGou2012-9020-ExpandSearchManager",
    )

    override suspend fun search(keyword: String, page: Int, pageSize: Int): List<Song> {
        val url = "https://songsearch.kugou.com/song_search_v2?keyword=${keyword.urlEncoded()}" +
            "&page=$page&pagesize=$pageSize&userid=0&clientver=&platform=WebFilter" +
            "&filter=2&iscorrection=1&privilege_filter=0&area_code=1"
        val response = http.get(url)
        if (!response.isSuccessful) return emptyList()
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return emptyList()
        if (root.optInt("error_code", -1) != 0) return emptyList()
        val list = root.optJSONObject("data")?.optJSONArray("lists") ?: return emptyList()

        return list.objects().mapNotNull { item ->
            val hash = item.optString("FileHash").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Audioid 可能为 0，此时用 hash 当作歌曲标识
            val songId = item.optString("Audioid").takeIf { it.isNotBlank() && it != "0" } ?: hash
            val cover = item.optString("Image").takeIf { it.isNotBlank() }?.replace("{size}", "480")
            buildSong(
                songId = songId,
                title = item.optString("SongName"),
                artist = item.optJSONArray("Singers").joinNames()
                    .ifBlank { item.optString("SingerName") },
                albumTitle = item.optString("AlbumName"),
                durationMs = item.optLong("Duration") * 1000,
                coverUri = cover,
                albumId = item.optString("AlbumID").takeIf { it.isNotBlank() },
                payload = item,
            )
        }
    }

    override suspend fun lyrics(songId: String, payload: String?): PlatformLyrics? {
        val data = payload?.let { runCatching { JSONObject(it) }.getOrNull() }
        val hash = data?.optString("FileHash")?.takeIf { it.isNotBlank() } ?: return null
        val name = data.optString("SongName").ifBlank { "" }
        val durationSeconds = data.optLong("Duration").takeIf { it > 0 } ?: 0L

        val searchUrl = "http://lyrics.kugou.com/search?ver=1&man=yes&client=pc" +
            "&keyword=${name.urlEncoded()}&hash=$hash&timelength=${durationSeconds * 1000}&lrctxt=1"
        val searchResponse = http.get(searchUrl, lyricHeaders)
        if (!searchResponse.isSuccessful) return null
        val candidate = runCatching { JSONObject(searchResponse.body) }.getOrNull()
            ?.optJSONArray("candidates")
            ?.objects()
            ?.firstOrNull()
            ?: return null
        val id = candidate.optString("id").takeIf { it.isNotBlank() } ?: return null
        val accessKey = candidate.optString("accesskey").takeIf { it.isNotBlank() } ?: return null
        // krctype==1 且 contenttype!=1 才是逐字 KRC；KRC 需要解密+解压，这里只取 LRC
        val fmt = "lrc"

        val downloadUrl = "http://lyrics.kugou.com/download?ver=1&client=pc&id=$id" +
            "&accesskey=$accessKey&fmt=$fmt&charset=utf8"
        val downloadResponse = http.get(downloadUrl, lyricHeaders)
        if (!downloadResponse.isSuccessful) return null
        val content = runCatching { JSONObject(downloadResponse.body) }.getOrNull()
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val lyric = runCatching {
            Base64.decode(content, Base64.DEFAULT).toString(Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

        return PlatformLyrics(lyric = lyric)
    }
}