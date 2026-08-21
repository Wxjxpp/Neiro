package com.wxjxpp.musicplayer.core.db

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

/** 歌词缓存：避免每次进播放页都重新解析文件。 */
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
)