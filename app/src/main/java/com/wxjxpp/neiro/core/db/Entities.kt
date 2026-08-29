package com.wxjxpp.neiro.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 实体。
 *
 * 与领域模型（core/model）分开：领域模型可以自由演进，
 * 这里只关心持久化字段与迁移，映射逻辑集中在 Mappers.kt。
 */

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "track_number") val trackNumber: Int?,
    @ColumnInfo(name = "disc_number") val discNumber: Int?,
    @ColumnInfo(name = "cover_uri") val coverUri: String?,
    @ColumnInfo(name = "cover_seed") val coverSeedColor: Long,

    /** 位置：local / webdav / remote */
    @ColumnInfo(name = "location_type") val locationType: String,
    @ColumnInfo(name = "location_uri") val locationUri: String,
    @ColumnInfo(name = "location_extra") val locationExtra: String?,

    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "bitrate_kbps") val bitrateKbps: Int?,
    @ColumnInfo(name = "sample_rate_hz") val sampleRateHz: Int?,
    val channels: Int?,
    @ColumnInfo(name = "bit_depth") val bitDepth: Int?,

    @ColumnInfo(name = "rg_track_gain") val trackGainDb: Float?,
    @ColumnInfo(name = "rg_track_peak") val trackPeak: Float?,
    @ColumnInfo(name = "rg_album_gain") val albumGainDb: Float?,
    @ColumnInfo(name = "rg_album_peak") val albumPeak: Float?,

    /** 搜索用扩展字段 */
    @ColumnInfo(name = "release_date") val releaseDate: String?,
    val description: String?,
    /** 逗号分隔 */
    val tags: String?,

    @ColumnInfo(name = "metadata_complete") val metadataComplete: Boolean,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "cover_uri") val coverUri: String?,
    val description: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 歌单与歌曲的关联，position 决定顺序。 */
@Entity(tableName = "playlist_songs", primaryKeys = ["playlist_id", "song_id"])
data class PlaylistSongEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "song_id") val songId: String,
    val position: Int,
)

@Entity(tableName = "play_events")
data class PlayEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "listened_ms") val listenedMs: Long,
    val completed: Boolean,
)

@Entity(tableName = "diary_entries")
data class DiaryEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "date_ms") val dateMs: Long,
    @ColumnInfo(name = "song_id") val songId: String?,
    val mood: String?,
    val note: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)

/**
 * 歌词缓存：避免每次进播放页都重新解析文件。
 *
 * ## 为什么需要 [parserVersion]
 *
 * [payload] 存的是**解析后的结果**（startMs / text / translation / syllables），
 * 不是原始歌词文本。因此一旦解析器有 bug，错误结果会被永久固化在缓存里 ——
 * `lyricsFor` 命中缓存就直接返回，修好的解析器代码根本不会执行。
 *
 * 实测踩过两次：v8 之前把翻译与原文存反、把音节的词间空格 trim 掉了，
 * 修完解析器后装新包依然显示旧结果，因为读的全是旧缓存。
 *
 * 现在每条缓存都记下写入时的解析器版本，版本不符即视为失效重新解析。
 * 用户手动指定的歌词（[isOverride]）不受影响 —— 那不是解析产物。
 */
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey @ColumnInfo(name = "song_id") val songId: String,
    val format: String,
    @ColumnInfo(name = "offset_ms") val offsetMs: Long,
    /** 序列化后的歌词内容（JSON） */
    val payload: String,
    /** 用户手动指定/校准过，优先级高于自动查找 */
    @ColumnInfo(name = "is_override") val isOverride: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /**
     * 写入这条缓存时的解析器版本，见 [LYRICS_PARSER_VERSION]。
     *
     * 默认 0 表示"版本未知的历史数据"，一定与当前版本不符，因此会被重新解析。
     */
    @ColumnInfo(name = "parser_version", defaultValue = "0") val parserVersion: Int = 0,
)

/**
 * 歌词解析器的行为版本号。**任何改变解析输出的修改都必须让它 +1**，
 * 否则用户设备上的旧缓存会继续生效，修复不会生效。
 *
 * 变更历史：
 * - 1：引入版本号时的基线（SPL 标准重写 + 音节空格保留 + 主歌词位次判定）
 */
const val LYRICS_PARSER_VERSION = 2

/** 应用启动记录：听歌热力图的"启动次数"维度，每次冷启动一行。 */
@Entity(tableName = "app_launches")
data class AppLaunchEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "launched_at_ms") val launchedAtMs: Long,
)