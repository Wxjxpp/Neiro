package com.wxjxpp.neiro.core.source

import com.wxjxpp.neiro.core.model.Lyrics
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.Quality
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.source.online.OnlinePlatform
import com.wxjxpp.neiro.core.source.online.toScriptQuality
import com.wxjxpp.neiro.core.userapi.UserApiClient
import org.json.JSONObject

/**
 * 在线音源。
 *
 * 职责切分清楚，避免把两类失败混在一起：
 * - **搜索 / 歌词 / 封面**：走 [platform] 的公开接口，不需要用户导入任何脚本
 * - **播放地址**：优先平台官方接口（目前仅网易云 weapi，免费歌免登录），
 *   失败再走 [userApiClient]（LX 协议 `musicUrl`，需已启用的自定义音源脚本）
 *
 * 因此"能搜到但放不了"是正常状态，此时 [resolvePlayUrlDetailed] 会给出
 * 明确原因（没有音源 / 脚本不支持该平台 / 脚本报错），由 UI 提示用户。
 */
class OnlineMusicSource(
    private val platform: OnlinePlatform,
    private val userApiClient: UserApiClient,
    /** 当前启用脚本声明支持的平台 → 动作，用于提前判断能否取流。 */
    private val supportedActions: () -> Map<String, List<String>>,
    /** LX 音源专用：完全由脚本取流，失败原因直接透传给 UI。 */
    private val streamResolver: (suspend (Song, Quality) -> PlayUrlResult)? = null,
) : MusicSource {
    override val id: String = platform.id
    override val displayName: String = platform.displayName

    /**
     * 脚本能力表的查询键：LX 派生源（id 形如 "wy-lx"）剥掉后缀，
     * 与脚本 init 时上报的协议平台标识（wy/kw/kg/tx/mg）对齐。
     */
    val scriptPlatformId: String get() = id.removeSuffix("-lx")

    override val capabilities: Set<SourceCapability> = buildSet {
        add(SourceCapability.Search)
        add(SourceCapability.QualitySelection)
        if (platform.supportsLyrics) add(SourceCapability.Lyrics)
    }

    /** 取流结果，带失败原因。 */
    sealed interface PlayUrlResult {
        data class Success(val url: String) : PlayUrlResult
        data class Failure(val reason: String) : PlayUrlResult
    }

    override suspend fun search(query: String, page: Int, pageSize: Int): List<Song> =
        runCatching { platform.search(query, page, pageSize) }.getOrDefault(emptyList())

    override suspend fun resolvePlayUrl(song: Song, quality: Quality): String? =
        (resolvePlayUrlDetailed(song, quality) as? PlayUrlResult.Success)?.url

    /** 与 [resolvePlayUrl] 相同，但失败时返回原因而不是 null。 */
    suspend fun resolvePlayUrlDetailed(song: Song, quality: Quality): PlayUrlResult {
        val remote = song.location as? MediaLocation.Remote
            ?: return PlayUrlResult.Failure("这不是在线歌曲")
        // 0. LX 音源：完全由脚本取流，不经过官方接口
        streamResolver?.let { resolver -> return resolver(song, quality) }
        // 1. 平台官方接口通道已移除（仅外置音源策略），直接走脚本判定
        val actions = supportedActions()
        if (actions.isEmpty()) {
            return PlayUrlResult.Failure(
                "官方接口取流失败（VIP 或无版权歌曲）。可在「自定义音源」导入 LX 脚本解锁，" +
                    "或在设置中填写网易云 Cookie"
            )
        }
        val platformActions = actions[platform.id]
        if (platformActions == null || "musicUrl" !in platformActions) {
            return PlayUrlResult.Failure("当前音源脚本不支持${platform.displayName}，请换一个脚本或搜索其他平台")
        }

        val musicInfo = remote.musicInfo(song)
        val result = userApiClient.musicUrl(
            source = platform.id,
            quality = quality.toScriptQuality(),
            musicInfo = musicInfo,
        )
        return when (result) {
            is UserApiClient.Result.Success ->
                result.dataJson?.let { PlayUrlResult.Success(it) }
                    ?: PlayUrlResult.Failure("音源脚本返回了空地址")

            is UserApiClient.Result.Failure -> PlayUrlResult.Failure(result.reason)
        }
    }

    override suspend fun fetchLyricsRaw(song: Song): RawLyrics? {
        val remote = song.location as? MediaLocation.Remote ?: return null

        // 先用平台公开接口；拿不到再退回脚本
        platform.takeIf { it.supportsLyrics }
            ?.let { runCatching { it.lyrics(remote.songId, remote.payload) }.getOrNull() }
            ?.let { lyrics ->
                return RawLyrics(
                    content = lyrics.lyric,
                    declaredFormat = if (lyrics.isTtml) "ttml" else "lrc",
                    translationContent = lyrics.tlyric,
                    romanizationContent = lyrics.rlyric,
                    wordByWordContent = lyrics.lxlyric,
                )
            }

        if (supportedActions()[platform.id]?.contains("lyric") != true) return null
        val result = userApiClient.lyric(platform.id, remote.musicInfo(song))
        val json = (result as? UserApiClient.Result.Success)?.dataJson
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return null
        val lyric = json.optString("lyric").takeIf { it.isNotBlank() } ?: return null
        return RawLyrics(
            content = lyric,
            declaredFormat = "lrc",
            translationContent = json.optString("tlyric").takeIf { it.isNotBlank() },
            romanizationContent = json.optString("rlyric").takeIf { it.isNotBlank() },
            wordByWordContent = json.optString("lxlyric").takeIf { it.isNotBlank() },
        )
    }

    override suspend fun fetchLyrics(song: Song): Lyrics? = null

    /**
     * 构造交给脚本的 musicInfo。
     *
     * 优先原样使用搜索时保存的平台 JSON —— 脚本依赖 `hash`、`copyrightId`、
     * `strMediaMid` 这些平台特有字段。payload 缺失时退化成最小可用结构。
     */
    private fun MediaLocation.Remote.musicInfo(song: Song): JSONObject {
        payload?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()?.let { json ->
                // 补齐 LX 脚本习惯读取的通用字段
                if (!json.has("songmid")) json.put("songmid", songId)
                if (!json.has("source")) json.put("source", sourceId)
                if (!json.has("name")) json.put("name", song.title)
                if (!json.has("singer")) json.put("singer", song.artistName)
                if (!json.has("albumName")) json.put("albumName", song.albumTitle)
                return json
            }
        }
        return JSONObject().apply {
            put("songmid", songId)
            put("source", sourceId)
            put("name", song.title)
            put("singer", song.artistName)
            put("albumName", song.albumTitle)
            put("interval", song.durationMs / 1000)
        }
    }
}