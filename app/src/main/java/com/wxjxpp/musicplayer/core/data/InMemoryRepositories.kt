package com.wxjxpp.musicplayer.core.data

import com.wxjxpp.musicplayer.core.model.Album
import com.wxjxpp.musicplayer.core.model.Artist
import com.wxjxpp.musicplayer.core.model.AudioFormat
import com.wxjxpp.musicplayer.core.model.DiaryEntry
import com.wxjxpp.musicplayer.core.model.ListeningReport
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.MediaLocation
import com.wxjxpp.musicplayer.core.model.PlayEvent
import com.wxjxpp.musicplayer.core.model.Playlist
import com.wxjxpp.musicplayer.core.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * 内存实现，用于在接入 Room / 真实扫描之前跑通全链路。
 *
 * 每个类都可以被同名接口的真实实现替换，替换点只有依赖装配处。
 */

class InMemorySongRepository : SongRepository {

    // 主线不再注入占位歌曲；真实歌曲由 dev 分支的扫描器写入。
    private val songs = MutableStateFlow<List<Song>>(emptyList())

    override fun observeSongs(): Flow<List<Song>> = songs.asStateFlow()

    override suspend fun getSong(id: String): Song? = songs.value.firstOrNull { it.id == id }

    override suspend fun upsert(songs: List<Song>) {
        this.songs.update { current ->
            val map = current.associateBy { it.id }.toMutableMap()
            songs.forEach { map[it.id] = it }
            map.values.toList()
        }
    }

    override suspend fun deleteAll() {
        songs.value = emptyList()
    }

    override suspend fun rescanLocal() {
        // 主线尚未接入扫描器：刷新只保留当前曲库，不再回填示例歌曲。
        delay(300)
    }
}

class InMemoryPlaylistRepository : PlaylistRepository {

    private val playlists = MutableStateFlow<List<Playlist>>(emptyList())

    override fun observePlaylists(): Flow<List<Playlist>> = playlists.asStateFlow()

    override suspend fun create(name: String, songIds: List<String>): Playlist {
        val now = System.currentTimeMillis()
        val playlist = Playlist(
            id = "pl_$now",
            name = name,
            songIds = songIds,
            createdAt = now,
            updatedAt = now,
        )
        playlists.update { it + playlist }
        return playlist
    }

    override suspend fun rename(id: String, name: String) = mutate(id) { it.copy(name = name) }

    override suspend fun addSongs(playlistId: String, songIds: List<String>) =
        mutate(playlistId) { it.copy(songIds = (it.songIds + songIds).distinct()) }

    override suspend fun removeSongs(playlistId: String, songIds: List<String>) =
        mutate(playlistId) { it.copy(songIds = it.songIds - songIds.toSet()) }

    override suspend fun reorder(playlistId: String, songIds: List<String>) =
        mutate(playlistId) { it.copy(songIds = songIds) }

    override suspend fun delete(id: String) {
        playlists.update { list -> list.filterNot { it.id == id } }
    }

    private inline fun mutate(id: String, crossinline block: (Playlist) -> Playlist) {
        playlists.update { list ->
            list.map { if (it.id == id) block(it).copy(updatedAt = System.currentTimeMillis()) else it }
        }
    }
}

class InMemoryLyricsRepository : LyricsRepository {

    private val cache = mutableMapOf<String, Lyrics>()

    override suspend fun lyricsFor(song: Song): Lyrics = cache[song.id] ?: Lyrics.Empty

    override suspend fun cache(songId: String, lyrics: Lyrics) {
        cache[songId] = lyrics
    }

    override suspend fun override(songId: String, lyrics: Lyrics) {
        cache[songId] = lyrics
    }
}

class InMemoryStatsRepository : StatsRepository {

    private val events = MutableStateFlow<List<PlayEvent>>(emptyList())

    override suspend fun record(event: PlayEvent) {
        events.update { it + event }
    }

    override fun observeRecent(limit: Int): Flow<List<PlayEvent>> =
        events.map { it.takeLast(limit).reversed() }

    override suspend fun report(fromMs: Long, toMs: Long, label: String): ListeningReport {
        val inRange = events.value.filter { it.startedAtMs in fromMs..toMs }
        return ListeningReport(
            periodLabel = label,
            totalSongs = inRange.map { it.songId }.distinct().size,
            totalDurationMs = inRange.sumOf { it.listenedMs },
        )
    }
}

class InMemoryDiaryRepository : DiaryRepository {

    private val entries = MutableStateFlow<List<DiaryEntry>>(emptyList())

    override fun observeEntries(): Flow<List<DiaryEntry>> = entries.asStateFlow()

    override suspend fun upsert(entry: DiaryEntry) {
        entries.update { list ->
            if (list.any { it.id == entry.id }) list.map { if (it.id == entry.id) entry else it }
            else list + entry
        }
    }

    override suspend fun delete(id: String) {
        entries.update { list -> list.filterNot { it.id == id } }
    }
}

class InMemorySettingsRepository : SettingsRepository {

    private val floatingBar = MutableStateFlow(true)
    private val replayGain = MutableStateFlow(false)
    private val showTranslation = MutableStateFlow(true)

    override fun observeFloatingPlayerBar(): Flow<Boolean> = floatingBar.asStateFlow()
    override suspend fun setFloatingPlayerBar(enabled: Boolean) {
        floatingBar.value = enabled
    }

    override fun observeReplayGainEnabled(): Flow<Boolean> = replayGain.asStateFlow()
    override suspend fun setReplayGainEnabled(enabled: Boolean) {
        replayGain.value = enabled
    }

    override fun observeShowTranslation(): Flow<Boolean> = showTranslation.asStateFlow()
    override suspend fun setShowTranslation(enabled: Boolean) {
        showTranslation.value = enabled
    }
}

/** 演示数据。接入真实扫描后删除本文件的这一部分即可。 */
private object SampleLibrary {

    private fun song(
        id: String,
        title: String,
        artist: String,
        album: String,
        seconds: Int,
        color: Long,
    ) = Song(
        id = id,
        title = title,
        artists = listOf(Artist(id = "ar_$id", name = artist)),
        album = Album(id = "al_$id", title = album, artistName = artist),
        durationMs = seconds * 1000L,
        coverSeedColor = color,
        location = MediaLocation.Local(uri = "sample://$id", filePath = null),
        format = AudioFormat(mimeType = "audio/flac", bitrateKbps = 1000, sampleRateHz = 44100),
        metadataComplete = true,
    )

    val songs = listOf(
        song("1", "Weightless", "Marconi Union", "Ambient Works", 488, 0xFF6750A4),
        song("2", "Night Owl", "Broke For Free", "Directionless EP", 195, 0xFF00696D),
        song("3", "Sunset Drive", "Kaisar", "Neon City", 242, 0xFF8E4B10),
        song("4", "Paper Plane", "Lumine", "Skyline", 200, 0xFF4A5C92),
        song("5", "Slow Motion", "Rhodes Trip", "Analog Days", 305, 0xFF7D5260),
        song("6", "Aurora", "Silver Lake", "Northern Lights", 361, 0xFF3F6837),
        song("7", "Deep Blue", "Ocean Drift", "Tides", 280, 0xFF00639A),
        song("8", "City Rain", "Neko Neko", "Umbrella", 230, 0xFF9A4058),
    )
}