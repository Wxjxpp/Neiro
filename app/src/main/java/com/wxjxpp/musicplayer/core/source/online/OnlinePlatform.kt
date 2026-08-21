package com.wxjxpp.musicplayer.core.source.online

import com.wxjxpp.musicplayer.core.model.Song

/**
 * 在线平台适配契约。
 *
 * 这一层只负责"搜索"与"取歌词"——两者都能用公开接口直连完成。
 * **播放地址不在这里**：各平台的取流接口需要签名 / 会员票据，
 * 由用户导入的自定义音源脚本（LX 协议 `musicUrl`）负责，见 OnlineMusicSource。
 *
 * 新增平台 = 实现本接口 + 加进 [defaultOnlinePlatforms]。
 */
interface OnlinePlatform {

    /** 平台标识，必须与 LX 脚本约定一致：kw / kg / tx / wy / mg。 */
    val id: String

    val displayName: String

    /** 是否支持取歌词。 */
    val supportsLyrics: Boolean get() = true

    suspend fun search(keyword: String, page: Int, pageSize: Int): List<Song>

    /**
     * 取歌词原文。
     *
     * [payload] 是搜索时保存下来的平台原始 JSON，歌词接口往往需要里面的
     * hash / lyricUrl 等字段，只有 songId 是不够的。
     */
    suspend fun lyrics(songId: String, payload: String?): PlatformLyrics? = null
}

/**
 * 平台返回的歌词原文。
 *
 * 字段命名沿用 LX 协议，方便与脚本返回值走同一条解析路径。
 */
data class PlatformLyrics(
    /** 主歌词（LRC 或 TTML）。 */
    val lyric: String,
    /** 翻译。 */
    val tlyric: String? = null,
    /** 罗马音。 */
    val rlyric: String? = null,
    /** 逐字歌词（增强型 LRC）。 */
    val lxlyric: String? = null,
    /** [lyric] 是否为 TTML（含逐字时间轴），供上层选择解析器。 */
    val isTtml: Boolean = false,
)

/** 音质档位 → 各平台 / LX 脚本约定的音质字符串。 */
fun com.wxjxpp.musicplayer.core.model.Quality.toScriptQuality(): String = when (this) {
    com.wxjxpp.musicplayer.core.model.Quality.Low -> "128k"
    com.wxjxpp.musicplayer.core.model.Quality.Standard -> "320k"
    com.wxjxpp.musicplayer.core.model.Quality.High -> "320k"
    com.wxjxpp.musicplayer.core.model.Quality.Lossless -> "flac"
    com.wxjxpp.musicplayer.core.model.Quality.HiRes -> "hires"
}