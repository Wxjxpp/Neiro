package com.wxjxpp.musicplayer.core.source

import com.wxjxpp.musicplayer.core.data.SongRepository
import com.wxjxpp.musicplayer.core.model.MediaLocation
import com.wxjxpp.musicplayer.core.model.Quality
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.search.searchSongs
import kotlinx.coroutines.flow.first

/**
 * 本地曲库音源。
 *
 * 搜索走已建立的曲库索引；播放地址就是文件本身，无需网络解析。
 */
class LocalMusicSource(
    private val songRepository: SongRepository,
) : MusicSource {

    override val id: String = "local"
    override val displayName: String = "本地音乐"

    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.Search,
        SourceCapability.Offline,
        SourceCapability.Lyrics,
    )

    override suspend fun search(query: String, page: Int, pageSize: Int): List<Song> {
        val all = songRepository.observeSongs().first()
        return all.searchSongs(query)
            .drop((page - 1).coerceAtLeast(0) * pageSize)
            .take(pageSize)
    }

    override suspend fun resolvePlayUrl(song: Song, quality: Quality): String? =
        (song.location as? MediaLocation.Local)?.uri
}