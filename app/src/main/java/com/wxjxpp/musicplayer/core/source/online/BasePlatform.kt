package com.wxjxpp.musicplayer.core.source.online

import com.wxjxpp.musicplayer.core.model.Album
import com.wxjxpp.musicplayer.core.model.Artist
import com.wxjxpp.musicplayer.core.model.AudioFormat
import com.wxjxpp.musicplayer.core.model.MediaLocation
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.net.HttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * 平台适配共用工具。
 *
 * 各平台返回结构差异很大，但落到本应用的 [Song] 只需要少数字段，
 * 这里把重复的构造逻辑与 JSON 取值收敛在一处。
 */
internal abstract class BasePlatform(
    protected val http: HttpClient,
) : OnlinePlatform {

    /**
     * 构造在线歌曲。
     *
     * id 统一为 `平台:歌曲id`，保证跨平台唯一；[payload] 原样保存平台数据，
     * 后续取播放地址时要整体交给音源脚本。
     */
    protected fun buildSong(
        songId: String,
        title: String,
        artist: String,
        albumTitle: String,
        durationMs: Long,
        coverUri: String?,
        payload: JSONObject,
        albumId: String? = null,
    ): Song = Song(
        id = "$id:$songId",
        title = title.decodeEntities().ifBlank { "未知歌曲" },
        artists = listOf(
            Artist(
                id = "artist:${artist.ifBlank { "unknown" }}",
                name = artist.decodeEntities().ifBlank { "未知艺术家" },
            )
        ),
        album = Album(
            id = "album:${albumId ?: albumTitle}",
            title = albumTitle.decodeEntities().ifBlank { "未知专辑" },
            artistName = artist.decodeEntities().takeIf { it.isNotBlank() },
            coverUri = coverUri,
        ),
        durationMs = durationMs,
        coverUri = coverUri,
        location = MediaLocation.Remote(
            sourceId = id,
            songId = songId,
            payload = payload.toString(),
        ),
        format = AudioFormat(),
        // 在线歌曲的元数据已经齐全，不需要再本地补齐
        metadataComplete = true,
        tags = listOf(displayName),
    )

    /**
     * 各平台返回的文本里常见 HTML 实体。
     *
     * 实体名用字符串拼接写出，避免源码经过任何 HTML 处理时被提前解码。
     */
    protected fun String.decodeEntities(): String {
        val amp = "&"
        var result = this
        result = result.replace(amp + "quot;", "\"")
        result = result.replace(amp + "apos;", "'")
        result = result.replace(amp + "#39;", "'")
        result = result.replace(amp + "lt;", "<")
        result = result.replace(amp + "gt;", ">")
        result = result.replace(amp + "nbsp;", " ")
        // &amp; 必须最后替换，否则会把上面刚生成的实体再解一次
        result = result.replace(amp + "amp;", amp)
        return result.trim()
    }

    protected fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    protected fun JSONArray.strings(): List<String> =
        (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

    /** 拼接演唱者名：多个歌手用 `/` 分隔，与本地曲库保持一致。 */
    protected fun JSONArray?.joinNames(key: String = "name"): String {
        if (this == null) return ""
        return objects().mapNotNull { it.optString(key).takeIf { s -> s.isNotBlank() } }
            .joinToString(" / ")
    }

    protected fun String.urlEncoded(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}