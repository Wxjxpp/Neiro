package com.wxjxpp.neiro.core.search

import com.wxjxpp.neiro.core.model.Song

/** 搜索字段由领域模型集中定义，后续新增字段只改这里。 */
data class SearchableSongFields(
    val title: String,
    val artist: String,
    val album: String,
    val date: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
)

fun Song.searchFields(): SearchableSongFields = SearchableSongFields(
    title = title,
    artist = artistName,
    album = albumTitle,
    // TODO 在 Song 增加发行日期/描述/标签字段后直接映射
)

fun List<Song>.searchSongs(query: String): List<Song> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return this
    return filter { song ->
        val fields = song.searchFields()
        sequenceOf(fields.title, fields.artist, fields.album, fields.date, fields.description)
            .plus(fields.tags.asSequence())
            .any { it.lowercase().contains(normalized) }
    }
}