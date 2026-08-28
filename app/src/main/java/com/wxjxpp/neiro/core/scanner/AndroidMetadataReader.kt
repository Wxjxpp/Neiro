package com.wxjxpp.neiro.core.scanner

import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.wxjxpp.neiro.core.model.Album
import com.wxjxpp.neiro.core.model.Artist
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.Song

/**
 * Android 内置元数据读取器。
 *
 * MediaMetadataRetriever 能读取常见音频标签、封面、时长和部分技术参数。
 * ReplayGain 由 ReplayGainReader 从 FLAC/Vorbis/ID3 常见文本标签补齐；内嵌歌词由歌词读取器处理。
 */
class AndroidMetadataReader(
    private val replayGainReader: ReplayGainReader? = null,
    private val resolver: ContentResolver? = null,
) : MetadataReader {

    override suspend fun readMetadata(song: Song): Song {
        val local = song.location as? MediaLocation.Local ?: return song
        val retriever = MediaMetadataRetriever()
        return runCatching {
            val uri = Uri.parse(local.uri)
            if (uri.scheme == ContentResolver.SCHEME_CONTENT && resolver != null) {
                retriever.setDataSource(resolver, uri)
            } else {
                retriever.setDataSource(local.uri)
            }
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
            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                ?.toIntOrNull()
            val replayGain = replayGainReader?.read(local.uri) ?: song.replayGain
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            song.copy(
                title = title,
                artists = listOf(Artist("artist:$artist", artist)),
                album = Album("album:$album", album, artist),
                durationMs = duration,
                format = song.format.copy(
                    mimeType = mime ?: song.format.mimeType,
                    bitrateKbps = bitrate ?: song.format.bitrateKbps,
                    sampleRateHz = sampleRate ?: song.format.sampleRateHz,
                ),
                replayGain = replayGain,
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