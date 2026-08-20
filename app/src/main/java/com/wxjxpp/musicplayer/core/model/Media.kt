package com.wxjxpp.musicplayer.core.model

/**
 * 媒体领域模型。
 *
 * 这里只描述"业务概念"，不含任何数据来源细节。
 * 本地文件、WebDAV、API 音源都要把自己的数据映射成这些类型，
 * UI 层永远只认这些模型，因此新增音源不会波及界面。
 */

/** 歌曲在哪里、怎么取到流。新增音源时扩展这个 sealed interface。 */
sealed interface MediaLocation {
    /** 本地文件（MediaStore 或 SAF 扫描到的）。 */
    data class Local(val uri: String, val filePath: String?) : MediaLocation

    /** WebDAV / 云盘。 */
    data class WebDav(val serverId: String, val remotePath: String) : MediaLocation

    /** 在线 API 音源，播放地址需要按音质临时解析。 */
    data class Remote(val sourceId: String, val songId: String) : MediaLocation
}

/** 音质档位。解析播放地址时使用。 */
enum class Quality { Low, Standard, High, Lossless, HiRes }

/**
 * ReplayGain / 音量归一化信息。
 * 读取元数据时填充，播放器据此做增益补偿。
 */
data class ReplayGain(
    val trackGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumGainDb: Float? = null,
    val albumPeak: Float? = null,
)

/** 音频技术参数。 */
data class AudioFormat(
    val mimeType: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    val bitDepth: Int? = null,
)

data class Artist(
    val id: String,
    val name: String,
    val coverUri: String? = null,
)

data class Album(
    val id: String,
    val title: String,
    val artistName: String? = null,
    val coverUri: String? = null,
    val year: Int? = null,
)

/**
 * 歌曲。
 *
 * [coverUri] 为空时 UI 会退化为按 [coverSeedColor] 生成占位封面，
 * 这样在元数据还没读出来的时候界面也不会空白。
 */
data class Song(
    val id: String,
    val title: String,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    val durationMs: Long = 0L,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val coverUri: String? = null,
    val coverSeedColor: Long = 0xFF4F5B92,
    val location: MediaLocation,
    val format: AudioFormat = AudioFormat(),
    val replayGain: ReplayGain = ReplayGain(),
    /** 元数据是否已完整读取。扫描时可先建索引，再异步补齐。 */
    val metadataComplete: Boolean = false,
) {
    val artistName: String get() = artists.joinToString(" / ") { it.name }.ifEmpty { "未知艺术家" }
    val albumTitle: String get() = album?.title ?: "未知专辑"
}

/** 歌单。本地歌单、云端歌单、每日推荐都用它。 */
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String> = emptyList(),
    val coverUri: String? = null,
    val description: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)