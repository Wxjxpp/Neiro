package com.wxjxpp.neiro.core.source.online

import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.userapi.UserApiClient
import org.json.JSONObject

/**
 * LX 自定义音源平台。
 *
 * 与内置平台（网易/酷狗/QQ...）不同，它没有自己的搜索 HTTP 接口——
 * 搜索仍复用对应内置平台的公开接口，但取流完全交给已启用的
 * LX 脚本（QuickJS 引擎）。这样：
 * - 没导入脚本时：该音源不出现在搜索页（容器层按脚本列表过滤）
 * - 导入后：用户可在搜索页切到 "酷我 · LX" 等条目，结果必然可播
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

    /** LX 取流：完整 [Song]（含 payload）→ 直链。官方接口不参与。 */
    suspend fun streamUrl(song: Song, quality: String): String? {
        val remote = song.location as? MediaLocation.Remote ?: return null
        val result = userApiClient.musicUrl(
            source = sourceId,
            quality = quality,
            musicInfo = remote.musicInfo(song),
        )
        return (result as? UserApiClient.Result.Success)
            ?.dataJson
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.optJSONObject("data")
            ?.optString("url")
            ?.takeIf { it.startsWith("http") }
    }
}

/** 由内置平台派生对应 LX 音源；平台不在 LX 协议范围（wy/kw/kg/tx/mg）内时返回 null。 */
fun lxSourceOf(base: OnlinePlatform, client: UserApiClient): LxSourcePlatform? =
    when (base.id) {
        "wy", "kw", "kg", "tx", "mg" -> LxSourcePlatform(base, client, base.id)
        else -> null
    }
