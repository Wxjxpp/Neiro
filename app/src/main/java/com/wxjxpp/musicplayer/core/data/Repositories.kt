package com.wxjxpp.musicplayer.core.data

import com.wxjxpp.musicplayer.core.model.DiaryEntry
import com.wxjxpp.musicplayer.core.model.ListeningReport
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.PlayEvent
import com.wxjxpp.musicplayer.core.model.Playlist
import com.wxjxpp.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * 仓库层契约。
 *
 * 每个契约对应一块业务能力，后续用 Room / 网络 / WebDAV 实现。
 * ViewModel 只依赖这些接口，因此存储方案变化不会波及 UI。
 */

interface SongRepository {
    /** 曲库列表。数据变化后自动推送新值。 */
    fun observeSongs(): Flow<List<Song>>
    suspend fun getSong(id: String): Song?
    suspend fun upsert(songs: List<Song>)
    suspend fun deleteAll()
    /** 触发一次本地扫描并入库。 */
    suspend fun rescanLocal()
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    suspend fun create(name: String, songIds: List<String> = emptyList()): Playlist
    suspend fun rename(id: String, name: String)
    suspend fun addSongs(playlistId: String, songIds: List<String>)
    suspend fun removeSongs(playlistId: String, songIds: List<String>)
    suspend fun reorder(playlistId: String, songIds: List<String>)
    suspend fun delete(id: String)
}

interface LyricsRepository {
    /**
     * 按优先级取歌词：内嵌 → 同名外挂文件 → 在线音源 → 空。
     * 具体顺序由实现决定，调用方不关心。
     */
    suspend fun lyricsFor(song: Song): Lyrics
    suspend fun cache(songId: String, lyrics: Lyrics)
    /** 用户手动指定歌词文件或调整偏移后覆盖。 */
    suspend fun override(songId: String, lyrics: Lyrics)
}

interface StatsRepository {
    suspend fun record(event: PlayEvent)
    fun observeRecent(limit: Int = 100): Flow<List<PlayEvent>>
    suspend fun report(fromMs: Long, toMs: Long, label: String): ListeningReport
    /** 听歌热力图：[fromMs, toMs] 范围内按天聚合（含启动次数与歌曲标签）。 */
    suspend fun heatmap(fromMs: Long, toMs: Long): List<HeatmapDay>
    /** 记录一次应用冷启动。 */
    suspend fun recordAppLaunch()
}

interface DiaryRepository {
    fun observeEntries(): Flow<List<DiaryEntry>>
    suspend fun upsert(entry: DiaryEntry)
    suspend fun delete(id: String)
}

/** 设置项存储。实现建议用 DataStore。 */
interface SettingsRepository {
    fun observeFloatingPlayerBar(): Flow<Boolean>
    suspend fun setFloatingPlayerBar(enabled: Boolean)

    fun observeReplayGainEnabled(): Flow<Boolean>
    suspend fun setReplayGainEnabled(enabled: Boolean)

    fun observeShowTranslation(): Flow<Boolean>
    suspend fun setShowTranslation(enabled: Boolean)
}