package com.wxjxpp.neiro.core.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import com.wxjxpp.neiro.core.model.Album
import com.wxjxpp.neiro.core.model.Artist
import com.wxjxpp.neiro.core.model.AudioFormat
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * MediaStore 本地音频扫描器。
 *
 * 扫描阶段就把专辑封面 URI 取出来（`content://media/external/audio/albumart/{albumId}`），
 * 这样列表首帧就能显示真实封面，不必等元数据补齐。
 */
class AndroidMediaScanner(
    private val resolver: ContentResolver,
    /** 传入时对每首歌强制从文件重读标签（MediaStore 缓存的文本列可能过期）。 */
    private val metadataReader: MetadataReader? = null,
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
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        var total = 0

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
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val count = cursor.count
            var index = 0

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, mediaId)
                val albumId = cursor.getLong(albumIdCol)
                val title = cursor.getString(titleCol).orEmpty().ifBlank { "未知歌曲" }
                val artist = cursor.getString(artistCol).orEmpty()
                    .takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING } ?: "未知艺术家"
                val album = cursor.getString(albumCol).orEmpty()
                    .takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING } ?: "未知专辑"
                val year = cursor.getInt(yearCol).takeIf { it > 0 }
                // TRACK 高位是碟号：1001 表示 1 碟第 1 轨
                val rawTrack = cursor.getInt(trackCol)
                val disc = if (rawTrack > 1000) rawTrack / 1000 else null
                val track = if (rawTrack > 1000) rawTrack % 1000 else rawTrack.takeIf { it > 0 }

                val song = Song(
                    id = "media:$mediaId",
                    title = title,
                    artists = listOf(Artist(id = "artist:$artist", name = artist)),
                    album = Album(
                        id = "album:$albumId",
                        title = album,
                        artistName = artist,
                        coverUri = albumArtUri(albumId),
                        year = year,
                    ),
                    durationMs = cursor.getLong(durationCol),
                    trackNumber = track,
                    discNumber = disc,
                    coverUri = albumArtUri(albumId),
                    coverSeedColor = seedColorFor(album),
                    location = MediaLocation.Local(
                        uri = uri.toString(),
                        filePath = cursor.getString(dataCol),
                    ),
                    format = AudioFormat(mimeType = cursor.getString(mimeCol)),
                    releaseDate = year?.toString(),
                    // MediaStore DATE_MODIFIED 是秒，领域模型统一用毫秒。
                    addedAt = cursor.getLong(modifiedCol) * 1000L,
                )
                // 直接从文件重读最新标签：MediaStore 的文本列是入库时的缓存，
                // 用户用外部工具改了标签但 MediaStore 未重新索引时会拿到旧值
                val fresh = metadataReader?.readMetadata(song) ?: song
                index++
                total = index
                emit(ScanProgress.Found(fresh, index, count))
            }
        }

        emit(ScanProgress.Completed(total, System.currentTimeMillis() - startedAt))
    }.flowOn(Dispatchers.IO)

    /** MediaStore 专辑封面地址；Coil 能直接加载这个 content URI。 */
    private fun albumArtUri(albumId: Long): String =
        "content://media/external/audio/albumart/$albumId"

    /** 无封面时的占位底色：按专辑名散列，保证同专辑颜色一致。 */
    private fun seedColorFor(album: String): Long {
        val palette = listOf(
            0xFF4F5B92, 0xFF00696D, 0xFF8E4B10, 0xFF7D5260,
            0xFF3F6837, 0xFF00639A, 0xFF9A4058, 0xFF6750A4,
        )
        val index = (album.hashCode().toLong() and 0x7FFFFFFF) % palette.size
        return palette[index.toInt()]
    }
}