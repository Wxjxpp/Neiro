package com.wxjxpp.musicplayer.core.source

import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.MediaLocation
import com.wxjxpp.musicplayer.core.model.Quality
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.userapi.UserApiAction
import com.wxjxpp.musicplayer.core.userapi.UserApiEngine
import com.wxjxpp.musicplayer.core.userapi.UserApiInfo
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID

/**
 * 用户自定义音源适配器。
 *
 * 把 [UserApiEngine] 的"请求-响应"异步协议包装成挂起函数：
 * 发起调用后等待对应 requestKey 的 `response` 动作。
 *
 * 这样它就是一个普通 [MusicSource]，UI 与播放层无需知道背后跑的是 JS。
 */
class UserApiSource(
    private val engine: UserApiEngine,
    private val info: UserApiInfo,
    private val timeoutMs: Long = 15_000L,
) : MusicSource {

    override val id: String = "userapi:${info.id}"
    override val displayName: String = info.name

    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.Search,
        SourceCapability.Lyrics,
        SourceCapability.QualitySelection,
    )

    override suspend fun search(query: String, page: Int, pageSize: Int): List<Song> {
        val result = call(
            action = "search",
            payload = JSONObject().apply {
                put("keyword", query)
                put("page", page)
                put("limit", pageSize)
            },
        ) ?: return emptyList()
        return parseSongs(result)
    }

    override suspend fun resolvePlayUrl(song: Song, quality: Quality): String? {
        val remote = song.location as? MediaLocation.Remote ?: return null
        val result = call(
            action = "musicUrl",
            payload = JSONObject().apply {
                put("songId", remote.songId)
                put("source", remote.sourceId)
                put("quality", quality.toScriptQuality())
            },
        ) ?: return null
        return runCatching { JSONObject(result).optString("url").takeIf { it.isNotBlank() } }
            .getOrNull() ?: result.takeIf { it.startsWith("http") }
    }

    override suspend fun fetchLyricsRaw(song: Song): RawLyrics? {
        val remote = song.location as? MediaLocation.Remote ?: return null
        val result = call(
            action = "lyric",
            payload = JSONObject().apply {
                put("songId", remote.songId)
                put("source", remote.sourceId)
            },
        ) ?: return null
        val json = runCatching { JSONObject(result) }.getOrNull()
        return RawLyrics(
            content = json?.optString("lyric").orEmpty().ifBlank { result },
            declaredFormat = "lrc",
            translationContent = json?.optString("tlyric")?.takeIf { it.isNotBlank() },
            romanizationContent = json?.optString("rlyric")?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun fetchLyrics(song: Song): Lyrics? = null

    /** 发起一次脚本调用并等待其 response。 */
    private suspend fun call(action: String, payload: JSONObject): String? {
        val requestKey = UUID.randomUUID().toString()
        payload.put("requestKey", requestKey)
        engine.callAction(action, payload.toString())

        val response = withTimeoutOrNull(timeoutMs) {
            engine.actions
                .filterIsInstance<UserApiAction.Response>()
                .first { it.requestKey == requestKey }
        } ?: return null

        return if (response.status) response.resultJson else null
    }

    private fun parseSongs(resultJson: String): List<Song> = runCatching {
        val root = JSONObject(resultJson)
        val array = root.optJSONArray("list") ?: return emptyList()
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val songId = o.optString("songmid").ifBlank { o.optString("id") }
            if (songId.isBlank()) return@mapNotNull null
            Song(
                id = "$id:$songId",
                title = o.optString("name").ifBlank { "未知歌曲" },
                artists = listOf(
                    com.wxjxpp.musicplayer.core.model.Artist(
                        id = "artist:${o.optString("singer")}",
                        name = o.optString("singer").ifBlank { "未知艺术家" },
                    )
                ),
                album = com.wxjxpp.musicplayer.core.model.Album(
                    id = "album:${o.optString("albumName")}",
                    title = o.optString("albumName").ifBlank { "未知专辑" },
                ),
                durationMs = o.optLong("interval") * 1000,
                coverUri = o.optString("img").takeIf { it.isNotBlank() },
                location = MediaLocation.Remote(sourceId = info.id, songId = songId),
            )
        }
    }.getOrDefault(emptyList())

    /** 音质枚举 → 脚本使用的字符串（沿用 LX 约定）。 */
    private fun Quality.toScriptQuality(): String = when (this) {
        Quality.Low -> "128k"
        Quality.Standard -> "320k"
        Quality.High -> "320k"
        Quality.Lossless -> "flac"
        Quality.HiRes -> "flac24bit"
    }
}