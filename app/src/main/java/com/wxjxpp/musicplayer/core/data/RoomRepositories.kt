package com.wxjxpp.musicplayer.core.data

import com.wxjxpp.musicplayer.core.db.DiaryDao
import com.wxjxpp.musicplayer.core.db.LyricsDao
import com.wxjxpp.musicplayer.core.db.PlayEventDao
import com.wxjxpp.musicplayer.core.db.PlaylistDao
import com.wxjxpp.musicplayer.core.db.SongDao
import com.wxjxpp.musicplayer.core.db.toDomain
import com.wxjxpp.musicplayer.core.db.toEntity
import com.wxjxpp.musicplayer.core.lyrics.LyricsLocator
import com.wxjxpp.musicplayer.core.model.DiaryEntry
import com.wxjxpp.musicplayer.core.model.ListeningReport
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.PlayEvent
import com.wxjxpp.musicplayer.core.model.Playlist
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.scanner.MediaScanner
import com.wxjxpp.musicplayer.core.scanner.MetadataReader
import com.wxjxpp.musicplayer.core.scanner.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar

/** Room 落库实现，替换原来的内存版本。 */

class RoomSongRepository(
    private val dao: SongDao,
    private val scanner: MediaScanner,
    private val metadataReader: MetadataReader,
) : SongRepository {

    override fun observeSongs(): Flow<List<Song>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getSong(id: String): Song? = dao.findById(id)?.toDomain()

    override suspend fun upsert(songs: List<Song>) = dao.upsert(songs.map { it.toEntity() })

    override suspend fun deleteAll() = dao.deleteAll()

    /** 批量删除（长按多选用）。 */
    suspend fun delete(ids: List<String>) = dao.deleteByIds(ids)

    override suspend fun rescanLocal() {
        val found = mutableListOf<Song>()
        scanner.scan().collect { progress ->
            if (progress is ScanProgress.Found) found += progress.song
        }
        if (found.isEmpty()) return
        // 先落轻量索引让列表立刻可见
        dao.upsert(found.map { it.toEntity() })
        // 再逐个补齐标签/封面/码率
        val enriched = found.map { metadataReader.readMetadata(it) }
        dao.upsert(enriched.map { it.toEntity() })
    }
}

class RoomPlaylistRepository(
    private val dao: PlaylistDao,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        combine(dao.observeAll(), dao.observeAllRelations()) { playlists, relations ->
            val grouped = relations.groupBy { it.playlistId }
            playlists.map { entity ->
                val ids = grouped[entity.id].orEmpty().sortedBy { it.position }.map { it.songId }
                entity.toDomain(ids)
            }
        }

    override suspend fun create(name: String, songIds: List<String>): Playlist {
        val now = System.currentTimeMillis()
        val playlist = Playlist(id = "pl_$now", name = name, createdAt = now, updatedAt = now)
        dao.upsert(playlist.toEntity())
        if (songIds.isNotEmpty()) dao.addSongs(playlist.id, songIds, now)
        return playlist.copy(songIds = songIds)
    }

    override suspend fun rename(id: String, name: String) =
        dao.rename(id, name, System.currentTimeMillis())

    override suspend fun addSongs(playlistId: String, songIds: List<String>) =
        dao.addSongs(playlistId, songIds, System.currentTimeMillis())

    override suspend fun removeSongs(playlistId: String, songIds: List<String>) {
        dao.removeSongs(playlistId, songIds)
        dao.touch(playlistId, System.currentTimeMillis())
    }

    override suspend fun reorder(playlistId: String, songIds: List<String>) =
        dao.reorder(playlistId, songIds, System.currentTimeMillis())

    override suspend fun delete(id: String) {
        dao.clearRelations(id)
        dao.delete(id)
    }
}

class RoomLyricsRepository(
    private val dao: LyricsDao,
    private val locator: LyricsLocator,
) : LyricsRepository {

    override suspend fun lyricsFor(song: Song): Lyrics {
        dao.find(song.id)?.let { cached ->
            val lyrics = cached.toDomain()
            // 用户手动指定过就直接用；否则命中缓存也直接用，避免重复解析
            if (!lyrics.isEmpty) return lyrics
        }
        val found = locator.find(song)
        if (!found.isEmpty) dao.upsert(found.toEntity(song.id, isOverride = false))
        return found
    }

    override suspend fun cache(songId: String, lyrics: Lyrics) =
        dao.upsert(lyrics.toEntity(songId, isOverride = false))

    override suspend fun override(songId: String, lyrics: Lyrics) =
        dao.upsert(lyrics.toEntity(songId, isOverride = true))
}

class RoomStatsRepository(
    private val dao: PlayEventDao,
    private val songDao: SongDao,
) : StatsRepository {

    override suspend fun record(event: PlayEvent) = dao.insert(event.toEntity())

    override fun observeRecent(limit: Int): Flow<List<PlayEvent>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun report(fromMs: Long, toMs: Long, label: String): ListeningReport {
        val events = dao.inRange(fromMs, toMs)
        if (events.isEmpty()) return ListeningReport(periodLabel = label)

        val byCount = events.groupingBy { it.songId }.eachCount()
        val topIds = byCount.entries.sortedByDescending { it.value }.take(10).map { it.key }
        val topSongs = songDao.findByIds(topIds).map { it.toDomain() }
            .sortedByDescending { byCount[it.id] ?: 0 }

        // 24 小时分布：看出"深夜听歌"这类结论
        val histogram = IntArray(24)
        val calendar = Calendar.getInstance()
        events.forEach { event ->
            calendar.timeInMillis = event.startedAtMs
            histogram[calendar.get(Calendar.HOUR_OF_DAY)]++
        }

        return ListeningReport(
            periodLabel = label,
            totalSongs = byCount.size,
            totalDurationMs = events.sumOf { it.listenedMs },
            topSongs = topSongs,
            topArtists = topSongs.flatMap { it.artists }.distinctBy { it.name }.take(10),
            topAlbums = topSongs.mapNotNull { it.album }.distinctBy { it.title }.take(10),
            hourHistogram = histogram.toList(),
        )
    }
}

class RoomDiaryRepository(
    private val dao: DiaryDao,
) : DiaryRepository {
    override fun observeEntries(): Flow<List<DiaryEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(entry: DiaryEntry) = dao.upsert(entry.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}