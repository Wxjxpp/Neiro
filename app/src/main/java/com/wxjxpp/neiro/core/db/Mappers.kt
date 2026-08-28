package com.wxjxpp.neiro.core.db

import com.wxjxpp.neiro.core.model.Album
import com.wxjxpp.neiro.core.model.Artist
import com.wxjxpp.neiro.core.model.AudioFormat
import com.wxjxpp.neiro.core.model.DiaryEntry
import com.wxjxpp.neiro.core.model.LyricLine
import com.wxjxpp.neiro.core.model.LyricSyllable
import com.wxjxpp.neiro.core.model.Lyrics
import com.wxjxpp.neiro.core.model.LyricsFormat
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.PlayEvent
import com.wxjxpp.neiro.core.model.Playlist
import com.wxjxpp.neiro.core.model.ReplayGain
import com.wxjxpp.neiro.core.model.Song
import org.json.JSONArray
import org.json.JSONObject

/** 实体 ↔ 领域模型映射。只有这一处知道两边的字段对应关系。 */

fun Song.toEntity(addedAt: Long = System.currentTimeMillis()): SongEntity {
    val (type, uri, extra) = when (val loc = location) {
        is MediaLocation.Local -> Triple("local", loc.uri, loc.filePath)
        is MediaLocation.WebDav -> Triple("webdav", loc.remotePath, loc.serverId)
        is MediaLocation.Remote -> Triple("remote", loc.songId, loc.sourceId)
    }
    return SongEntity(
        id = id,
        title = title,
        artist = artistName,
        album = albumTitle,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        coverUri = coverUri,
        coverSeedColor = coverSeedColor,
        locationType = type,
        locationUri = uri,
        locationExtra = extra,
        mimeType = format.mimeType,
        bitrateKbps = format.bitrateKbps,
        sampleRateHz = format.sampleRateHz,
        channels = format.channels,
        bitDepth = format.bitDepth,
        trackGainDb = replayGain.trackGainDb,
        trackPeak = replayGain.trackPeak,
        albumGainDb = replayGain.albumGainDb,
        albumPeak = replayGain.albumPeak,
        releaseDate = releaseDate,
        description = description,
        tags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
        metadataComplete = metadataComplete,
        addedAt = addedAt,
    )
}

fun SongEntity.toDomain(): Song = Song(
    id = id,
    title = title,
    artists = listOf(Artist(id = "artist:$artist", name = artist)),
    album = Album(id = "album:$album", title = album, artistName = artist),
    durationMs = durationMs,
    trackNumber = trackNumber,
    discNumber = discNumber,
    coverUri = coverUri,
    coverSeedColor = coverSeedColor,
    location = when (locationType) {
        "webdav" -> MediaLocation.WebDav(serverId = locationExtra.orEmpty(), remotePath = locationUri)
        "remote" -> MediaLocation.Remote(sourceId = locationExtra.orEmpty(), songId = locationUri)
        else -> MediaLocation.Local(uri = locationUri, filePath = locationExtra)
    },
    format = AudioFormat(mimeType, bitrateKbps, sampleRateHz, channels, bitDepth),
    replayGain = ReplayGain(trackGainDb, trackPeak, albumGainDb, albumPeak),
    releaseDate = releaseDate,
    description = description,
    tags = tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
    metadataComplete = metadataComplete,
    addedAt = addedAt,
)

fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    coverUri = coverUri,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PlaylistEntity.toDomain(songIds: List<String>): Playlist = Playlist(
    id = id,
    name = name,
    songIds = songIds,
    coverUri = coverUri,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PlayEvent.toEntity(): PlayEventEntity =
    PlayEventEntity(id, songId, startedAtMs, listenedMs, completed)

fun PlayEventEntity.toDomain(): PlayEvent =
    PlayEvent(id, songId, startedAtMs, listenedMs, completed)

fun DiaryEntry.toEntity(): DiaryEntryEntity =
    DiaryEntryEntity(id, dateMs, songId, mood, note, createdAtMs)

fun DiaryEntryEntity.toDomain(): DiaryEntry =
    DiaryEntry(id, dateMs, songId, mood, note, createdAtMs)

/**
 * 歌词序列化。
 *
 * 用手写 JSON 而不是引入 kotlinx-serialization：字段少、只在缓存层用，
 * 避免为一个内部格式再加一条依赖与插件。
 */
fun Lyrics.toEntity(songId: String, isOverride: Boolean): LyricsCacheEntity {
    val array = JSONArray()
    lines.forEach { line ->
        array.put(
            JSONObject().apply {
                put("s", line.startMs)
                line.endMs?.let { put("e", it) }
                put("t", line.text)
                line.translation?.let { put("tr", it) }
                line.romanization?.let { put("ro", it) }
                line.agent?.let { put("ag", it) }
                if (line.syllables.isNotEmpty()) {
                    put(
                        "sy",
                        JSONArray().apply {
                            line.syllables.forEach { syl ->
                                put(
                                    JSONObject().apply {
                                        put("t", syl.text)
                                        put("s", syl.startMs)
                                        syl.endMs?.let { put("e", it) }
                                    }
                                )
                            }
                        }
                    )
                }
            }
        )
    }
    return LyricsCacheEntity(
        songId = songId,
        format = format.name,
        offsetMs = offsetMs,
        payload = array.toString(),
        isOverride = isOverride,
        updatedAt = System.currentTimeMillis(),
        parserVersion = LYRICS_PARSER_VERSION,
    )
}

fun LyricsCacheEntity.toDomain(): Lyrics = runCatching {
    val array = JSONArray(payload)
    val lines = (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        val syllablesJson = o.optJSONArray("sy")
        LyricLine(
            startMs = o.optLong("s"),
            endMs = if (o.has("e")) o.optLong("e") else null,
            text = o.optString("t"),
            translation = o.optString("tr").takeIf { it.isNotEmpty() },
            romanization = o.optString("ro").takeIf { it.isNotEmpty() },
            agent = o.optString("ag").takeIf { it.isNotEmpty() },
            syllables = if (syllablesJson == null) emptyList() else {
                (0 until syllablesJson.length()).map { j ->
                    val s = syllablesJson.getJSONObject(j)
                    LyricSyllable(
                        text = s.optString("t"),
                        startMs = s.optLong("s"),
                        endMs = if (s.has("e")) s.optLong("e") else null,
                    )
                }
            },
        )
    }
    Lyrics(
        format = runCatching { LyricsFormat.valueOf(format) }.getOrDefault(LyricsFormat.Unknown),
        lines = lines,
        offsetMs = offsetMs,
    )
}.getOrDefault(Lyrics.Empty)