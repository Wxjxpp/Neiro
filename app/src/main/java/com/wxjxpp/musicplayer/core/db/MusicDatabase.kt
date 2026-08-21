package com.wxjxpp.musicplayer.core.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<SongEntity>

    @Upsert
    suspend fun upsert(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_songs ORDER BY position ASC")
    fun observeAllRelations(): Flow<List<PlaylistSongEntity>>

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun clearRelations(playlistId: String)

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId AND song_id IN (:songIds)")
    suspend fun removeSongs(playlistId: String, songIds: List<String>)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun maxPosition(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(relations: List<PlaylistSongEntity>)

    @Query("SELECT song_id FROM playlist_songs WHERE playlist_id = :playlistId ORDER BY position ASC")
    suspend fun songIdsOf(playlistId: String): List<String>

    @Query("UPDATE playlists SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("UPDATE playlists SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    /** 追加歌曲到末尾，自动接续 position。 */
    @Transaction
    suspend fun addSongs(playlistId: String, songIds: List<String>, updatedAt: Long) {
        val existing = songIdsOf(playlistId).toSet()
        val toAdd = songIds.filterNot { it in existing }
        if (toAdd.isEmpty()) return
        var position = maxPosition(playlistId)
        insertRelations(toAdd.map { PlaylistSongEntity(playlistId, it, ++position) })
        touch(playlistId, updatedAt)
    }

    /** 整体重排：先清空再按新顺序写回。 */
    @Transaction
    suspend fun reorder(playlistId: String, songIds: List<String>, updatedAt: Long) {
        clearRelations(playlistId)
        insertRelations(songIds.mapIndexed { index, id -> PlaylistSongEntity(playlistId, id, index) })
        touch(playlistId, updatedAt)
    }
}

@Dao
interface PlayEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PlayEventEntity)

    @Query("SELECT * FROM play_events ORDER BY started_at_ms DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayEventEntity>>

    @Query("SELECT * FROM play_events WHERE started_at_ms BETWEEN :fromMs AND :toMs")
    suspend fun inRange(fromMs: Long, toMs: Long): List<PlayEventEntity>
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY date_ms DESC")
    fun observeAll(): Flow<List<DiaryEntryEntity>>

    @Upsert
    suspend fun upsert(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE song_id = :songId LIMIT 1")
    suspend fun find(songId: String): LyricsCacheEntity?

    @Upsert
    suspend fun upsert(entity: LyricsCacheEntity)

    @Delete
    suspend fun delete(entity: LyricsCacheEntity)
}

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlayEventEntity::class,
        DiaryEntryEntity::class,
        LyricsCacheEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun diaryDao(): DiaryDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        const val NAME = "music_player.db"
    }
}