package com.wxjxpp.musicplayer.core.scanner

import android.media.MediaMetadataRetriever
import com.wxjxpp.musicplayer.core.model.Album
import com.wxjxpp.musicplayer.core.model.Artist
import com.wxjxpp.musicplayer.core.model.MediaLocation
import com.wxjxpp.musicplayer.core.model.Song

/**
 * Android 内置元数据读取器。
 *
 * MediaMetadataRetriever 能读取常见音频标签、封面、时长和部分技术参数。
 * ReplayGain / 内嵌歌词在不同格式支持不一致，留给 taglib/jaudiotagger 实现补强。
 */
class AndroidMetadataReader : MetadataReader {

    override suspend fun readMetadata(song: Song): Song {
        val local = song.location as? MediaLocation.Local ?: return song
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(local.uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: song.title
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: song.artistName
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: song.albumTitle
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: song.durationMs
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()?.div(1000)
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            song.copy(
                title = title,
                artists = listOf(Artist("artist:$artist", artist)),
                album = Album("album:$album", album, artist),
                durationMs = duration,
                format = song.format.copy(mimeType = mime ?: song.format.mimeType, bitrateKbps = bitrate ?: song.format.bitrateKbps),
                metadataComplete = true,
            )
        }.getOrElse { song }.also { retriever.release() }
    }

    override suspend fun readArtwork(song: Song): ByteArray? {
        val local = song.location as? MediaLocation.Local ?: return null
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(local.uri)
            retriever.embeddedPicture
        }.getOrNull().also { retriever.release() }
    }
}