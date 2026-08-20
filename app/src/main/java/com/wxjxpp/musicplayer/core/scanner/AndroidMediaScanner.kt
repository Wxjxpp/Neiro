package com.wxjxpp.musicplayer.core.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import com.wxjxpp.musicplayer.core.model.MediaLocation
import com.wxjxpp.musicplayer.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * MediaStore 本地音频扫描器。
 *
 * 先建立轻量歌曲索引；详细标签/ReplayGain/歌词由 MetadataReader 后续补齐。
 * 这样大曲库不会在扫描阶段阻塞 UI。
 */
class AndroidMediaScanner(
    private val resolver: ContentResolver,
) : MediaScanner {

    override fun scan(roots: List<String>): Flow<ScanProgress> = flow {
        val startedAt = System.currentTimeMillis()
        emit(ScanProgress.Started)

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val songs = mutableListOf<Song>()

        resolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val total = cursor.count
            var index = 0

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, mediaId)
                val title = cursor.getString(titleCol).orEmpty().ifBlank { "未知歌曲" }
                val artist = cursor.getString(artistCol).orEmpty().ifBlank { "未知艺术家" }
                val album = cursor.getString(albumCol).orEmpty().ifBlank { "未知专辑" }
                val song = Song(
                    id = "media:$mediaId",
                    title = title,
                    artists = listOf(com.wxjxpp.musicplayer.core.model.Artist("artist:$artist", artist)),
                    album = com.wxjxpp.musicplayer.core.model.Album("album:$album", album, artist),
                    durationMs = cursor.getLong(durationCol),
                    location = MediaLocation.Local(uri.toString(), uri.toString()),
                    format = com.wxjxpp.musicplayer.core.model.AudioFormat(
                        mimeType = cursor.getString(mimeCol),
                    ),
                )
                songs += song
                index++
                emit(ScanProgress.Found(song, index, total))
            }
        }

        emit(ScanProgress.Completed(songs.size, System.currentTimeMillis() - startedAt))
    }.flowOn(Dispatchers.IO)
}