package com.wxjxpp.neiro.core.source.online

import android.util.Base64
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.net.HttpClient
import com.wxjxpp.neiro.core.net.NeteaseCrypto
import org.json.JSONObject

/**
 * 网易云音乐平台适配（完整版）。
 *
 * 播放方式移植自 lx-music-mobile 的 wy 音源（参考其 Python 移植实现）：
 * - 搜索：eapi batch `/api/search/song/list/page`（无需登录）
 * - 取流：weapi `/weapi/song/enhance/player/url/v1`
 *   无 Cookie 时免费歌曲可直接取到 URL；带 `MUSIC_U` Cookie 可解锁 VIP / 无版权歌曲
 * - 歌词：eapi `/api/song/lyric/v1`，`yv=1` 请求 yrc 逐字（需登录 Cookie）
 * - **AMLL TTML**：优先从 amll-ttml-db（jsDelivr CDN）按网易云 ID 直取逐字 TTML，
 *   命中即为完整逐字歌词，不依赖任何 Cookie
 *
 * Cookie 通过 [cookieProvider] 注入（设置页可配置），格式 `MUSIC_U=xxx`。
 */
internal class NeteasePlatform(
    http: HttpClient,
    private val cookieProvider: () -> String = { "" },
) : BasePlatform(http) {

    override val id: String = "wy"
    override val displayName: String = "网易云"

    private val pcUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36 Edg/108.0.1462.54"
    private val oldUa =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36"

    /** 会话预热：首次请求前访问主页建立 cookie。 */
    @Volatile
    private var warmedUp = false

    private suspend fun ensureWarm() {
        if (warmedUp) return
        warmedUp = true
        runCatching { http.get("https://music.163.com/") }
    }

    override suspend fun search(keyword: String, page: Int, pageSize: Int): List<Song> {
        ensureWarm()
        val form = NeteaseCrypto.eapi(
            "/api/search/song/list/page",
            mapOf(
                "keyword" to keyword,
                "needCorrect" to "1",
                "channel" to "typing",
                "offset" to (pageSize * (page - 1)).toString(),
                "scene" to "normal",
                "total" to (page == 1),
                "limit" to pageSize.toString(),
            ),
        )
        val response = http.postForm(
            "https://interface.music.163.com/eapi/batch",
            form,
            headers = mapOf(
                "User-Agent" to pcUa,
                "origin" to "https://music.163.com",
                "Referer" to "https://music.163.com/",
            ),
        )
        if (!response.isSuccessful) return emptyList()
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return emptyList()
        if (root.optInt("code", -1) != 200) return emptyList()
        val resources = root.optJSONObject("data")?.optJSONArray("resources") ?: return emptyList()

        return resources.objects().mapNotNull { item ->
            val info = item.optJSONObject("baseInfo")?.optJSONObject("simpleSongData")
                ?: return@mapNotNull null
            val songId = info.optLong("id").takeIf { it > 0 }?.toString() ?: return@mapNotNull null
            val album = info.optJSONObject("al")
            buildSong(
                songId = songId,
                title = info.optString("name"),
                artist = info.optJSONArray("ar").joinNames(),
                albumTitle = album?.optString("name").orEmpty(),
                durationMs = info.optLong("dt"),
                coverUri = album?.optString("picUrl")?.takeIf { it.isNotBlank() },
                albumId = album?.optLong("id")?.toString(),
                payload = info,
            )
        }
    }

    /**
     * 取播放地址。
     *
     * 策略（2026-08 实测）：
     * 1. **官方公开 API**（`/api/song/enhance/player/url`，GET，无加密）：
     *    weapi 取流接口已被风控（返回空 body），而这个老接口仍对未登录设备放行
     *    免费歌曲（fee=0/8）；带 `MUSIC_U` Cookie 时 VIP 歌也能直接出 URL。
     * 2. **weapi 加密接口**兜底：部分场景下仍可用（带 Cookie 时）。
     *
     * 返回 null 时上层会尝试音源脚本兜底。
     */
    suspend fun streamUrl(songId: String, quality: String): String? {
        ensureWarm()
        val cookie = cookieProvider()
        val br = when (quality) {
            "128k" -> 128000
            "flac" -> 999000
            "hires" -> 999000
            else -> 320000
        }
        // 1) 官方公开 API（GET，无需加密；Cookie 直接透传）
        runCatching {
            val response = http.get(
                "https://music.163.com/api/song/enhance/player/url" +
                    "?id=$songId&ids=%5B$songId%5D&br=$br",
                headers = buildMap {
                    put("User-Agent", pcUa)
                    put("Referer", "https://music.163.com")
                    if (cookie.isNotBlank()) put("Cookie", cookie)
                },
            )
            if (response.isSuccessful) {
                val root = runCatching { JSONObject(response.body) }.getOrNull()
                val url = root?.optJSONArray("data")?.optJSONObject(0)
                    ?.optString("url")
                    ?.takeIf { it.startsWith("http") }
                // CDN 返回的是 http://，统一升级 https（CDN 支持），
                // 避免 Android 9+ 明文流量限制导致 ExoPlayer 播放失败
                if (url != null) return url.replaceFirst("http://", "https://")
            }
        }
        // 2) weapi 加密接口兜底
        val level = when (quality) {
            "128k" -> "standard"
            "320k" -> "exhigh"
            "flac" -> "lossless"
            "hires" -> "hires"
            else -> "exhigh"
        }
        val encodeType = if (level == "hires") "flac" else "aac"
        val csrf = Regex("_csrf=([^(;|$)]+)").find(cookie)?.groupValues?.get(1) ?: ""
        val form = NeteaseCrypto.weapi(
            linkedMapOf(
                "ids" to "[$songId]",
                "level" to level,
                "encodeType" to encodeType,
                "csrf_token" to csrf,
            ),
        )
        val weapiResponse = http.postForm(
            "https://music.163.com/weapi/song/enhance/player/url/v1",
            form,
            headers = buildMap {
                put("User-Agent", pcUa)
                put("origin", "https://music.163.com")
                put("Referer", "https://music.163.com")
                if (cookie.isNotBlank()) put("Cookie", cookie)
            },
        )
        if (!weapiResponse.isSuccessful) return null
        val root = runCatching { JSONObject(weapiResponse.body) }.getOrNull() ?: return null
        if (root.optInt("code", -1) != 200) return null
        return root.optJSONArray("data")?.optJSONObject(0)
            ?.optString("url")
            ?.takeIf { it.startsWith("http") }
    }

    override suspend fun lyrics(songId: String, payload: String?): PlatformLyrics? {
        // 1. AMLL TTML 逐字库直取（无需 Cookie，命中即最优）
        fetchAmllTtml(songId)?.let { ttml ->
            // TTML 原文交给上层解析器；翻译已在 TTML 内部
            return PlatformLyrics(lyric = ttml).copy(isTtml = true)
        }

        // 2. eapi 官方歌词（lrc/tlyric 免登录；yrc 逐字需 Cookie）
        ensureWarm()
        val form = NeteaseCrypto.eapi(
            "/api/song/lyric/v1",
            mapOf(
                "id" to songId,
                "cp" to false,
                "tv" to 0,
                "lv" to 0,
                "rv" to 0,
                "kv" to 0,
                "yv" to 1,
                "ytv" to 0,
                "yrv" to 0,
            ),
        )
        val response = http.postForm(
            "https://interface3.music.163.com/eapi/song/lyric/v1",
            form,
            headers = mapOf(
                "User-Agent" to oldUa,
                "origin" to "https://music.163.com",
            ),
        )
        if (!response.isSuccessful) return null
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        if (root.optInt("code", -1) != 200) return null

        val lrc = root.optJSONObject("lrc")?.optString("lyric").orEmpty()
        if (lrc.isBlank()) return null
        // 头部 JSON 行（{"t":..,"c":[..]}）转成普通 LRC 行
        val fixedLrc = fixJsonHeaderLines(lrc)
        return PlatformLyrics(
            lyric = fixedLrc,
            tlyric = root.optJSONObject("tlyric")?.optString("lyric")?.takeIf { it.isNotBlank() },
            rlyric = root.optJSONObject("romalrc")?.optString("lyric")?.takeIf { it.isNotBlank() },
            lxlyric = root.optJSONObject("yrc")?.optString("lyric")?.takeIf { it.isNotBlank() },
        )
    }

    /** 从 amll-ttml-db 按 ncmMusicId 直取 TTML。 */
    private suspend fun fetchAmllTtml(songId: String): String? {
        val response = runCatching {
            http.get(
                "https://cdn.jsdelivr.net/gh/amll-dev/amll-ttml-db@main/ncm-lyrics/$songId.ttml",
                headers = mapOf("User-Agent" to "Mozilla/5.0"),
            )
        }.getOrNull() ?: return null
        return response.takeIf { it.isSuccessful && it.body.contains("<tt", ignoreCase = true) }?.body
    }

    /** 网易云 LRC 头部的 JSON 元数据行转成 `[mm:ss.xx]文本`。 */
    private fun fixJsonHeaderLines(lrc: String): String =
        lrc.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("{\"t\"")) {
                line
            } else {
                runCatching {
                    val json = JSONObject(trimmed)
                    val ms = json.optLong("t", 0)
                    val text = json.optJSONArray("c")?.let { arr ->
                        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("tx") }
                            .joinToString("")
                    } ?: ""
                    if (text.isBlank()) "" else "[%02d:%02d.%02d]%s".format(
                        ms / 60000,
                        (ms % 60000) / 1000,
                        (ms % 1000) / 10,
                        text,
                    )
                }.getOrDefault(line)
            }
        }
}