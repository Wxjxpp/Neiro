package com.wxjxpp.neiro.core.source.online

import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.Quality
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.source.OnlineMusicSource
import com.wxjxpp.neiro.core.userapi.UserApiClient
import com.wxjxpp.neiro.core.source.online.toScriptQuality
import org.json.JSONObject

/**
 * LX 自定义音源平台。
 *
 * 与内置平台（网易/酷狗/QQ...）不同，它没有自己的搜索 HTTP 接口——
 * 搜索仍复用对应内置平台的公开接口，但取流完全交给已启用的
 * LX 脚本（QuickJS 引擎）。
 *
 * [base] 提供搜索与 payload 构造；[sourceId] 是 LX 协议的平台标识
 * （kw / kg / tx / wy / mg）。
 */
class LxSourcePlatform(
    private val base: OnlinePlatform,
    private val userApiClient: UserApiClient,
    private val sourceId: String,
) : OnlinePlatform by base {
    override val id: String = "$sourceId-lx"
    override val displayName: String = "${base.displayName} · LX"

    /**
     * 搜索复用 [base] 的公开接口，但结果必须重挂到本 LX 源：
     * 内置官方源已从注册表移除，若保留 base 的 sourceId，
     * 播放/下载时按 sourceId 查注册表会得到"找不到音源"。
     */
    override suspend fun search(keyword: String, page: Int, pageSize: Int): List<Song> =
        base.search(keyword, page, pageSize).map { song ->
            val remote = song.location as? MediaLocation.Remote
            if (remote != null) {
                song.copy(
                    id = "$id:${remote.songId}",
                    tags = listOf(displayName),
                    location = remote.copy(sourceId = id),
                )
            } else {
                song
            }
        }

    /** LX 取流：完整 [Song]（含 payload）→ 直链；失败时透传脚本真实报错。 */
    suspend fun streamUrl(song: Song, quality: Quality): OnlineMusicSource.PlayUrlResult {
        val remote = song.location as? MediaLocation.Remote
            ?: return OnlineMusicSource.PlayUrlResult.Failure("这不是在线歌曲")
        val result = userApiClient.musicUrl(
            source = sourceId,
            quality = quality.toScriptQuality(),
            musicInfo = buildMusicInfo(song, remote),
        )
        return when (result) {
            is UserApiClient.Result.Success -> {
                val url = result.dataJson
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                    ?.optJSONObject("data")
                    ?.optString("url")
                    ?.takeIf { it.startsWith("http") }
                if (url != null) {
                    OnlineMusicSource.PlayUrlResult.Success(url)
                } else {
                    // 脚本返回了 200 但内容里没有可用地址：给出原始返回便于定位
                    OnlineMusicSource.PlayUrlResult.Failure(
                        "脚本未返回播放地址，原始返回：${result.dataJson?.take(200)}"
                    )
                }
            }
            is UserApiClient.Result.Failure ->
                // 脚本内部异常原样透传（如"所有后端均失败"及各后端明细）
                OnlineMusicSource.PlayUrlResult.Failure(
                    result.reason.take(300)
                )
        }
    }

    companion object {
        /**
         * 构造 LX 协议的 musicInfo：优先使用平台搜索时保存的原始 JSON
         * （脚本依赖 hash/copyrightId 等平台字段），缺失时退化最小结构。
         */
        internal fun buildMusicInfo(song: Song, remote: MediaLocation.Remote): JSONObject {
            remote.payload?.let { raw ->
                runCatching { JSONObject(raw) }.getOrNull()?.let { json ->
                    if (!json.has("songmid")) json.put("songmid", remote.songId)
                    if (!json.has("source")) json.put("source", lxSourceId(remote.sourceId))
                    if (!json.has("name")) json.put("name", song.title)
                    if (!json.has("singer")) json.put("singer", song.artistName)
                    return json
                }
            }
            return JSONObject().apply {
                put("songmid", remote.songId)
                put("hash", remote.songId)
                put("source", lxSourceId(remote.sourceId))
                put("name", song.title)
                put("singer", song.artistName)
                put("albumName", song.albumTitle)
            }
        }

        /**
         * 从 Song.id / sourceId 还原 LX 协议平台标识。
         *
         * LX 派生源的 id 形如 "wy-lx"（[LxSourcePlatform.id]），内置源是 "wy"；
         * 脚本协议只认 "wy/kw/kg/tx/mg"，所以必须把 "-lx" 后缀剥掉。
         */
        internal fun lxSourceId(rawSourceId: String): String =
            rawSourceId.substringBefore(':').removeSuffix("-lx")
    }
}

/** 由内置平台派生对应 LX 音源；平台不在 LX 协议范围（wy/kw/kg/tx/mg）内时返回 null。 */
fun lxSourceOf(base: OnlinePlatform, client: UserApiClient): LxSourcePlatform? =
    when (base.id) {
        "wy", "kw", "kg", "tx", "mg" -> LxSourcePlatform(base, client, base.id)
        else -> null
    }
