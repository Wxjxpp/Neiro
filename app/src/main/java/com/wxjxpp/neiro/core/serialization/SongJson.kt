package com.wxjxpp.neiro.core.serialization

import com.wxjxpp.neiro.core.model.Album
import com.wxjxpp.neiro.core.model.Artist
import com.wxjxpp.neiro.core.model.AudioFormat
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * Song ⇄ JSON 序列化。
 *
 * 用途：播放快照持久化（跨会话恢复在线歌曲）、最近播放记录。
 * 在线歌曲不在本地曲库（Room）里，只存 songId 无法恢复，
 * 必须把整首歌的元数据（含 Remote payload）序列化下来。
 */
object SongJson {

    fun toJson(song: Song): String {
        val obj = JSONObject()
        obj.put("id", song.id)
        obj.put("title", song.title)
        obj.put("durationMs", song.durationMs)
        obj.put("coverUri", song.coverUri ?: "")
        obj.put("coverSeedColor", song.coverSeedColor)
        obj.put("addedAt", song.addedAt)
        obj.put("artists", JSONArray().apply {
            song.artists.forEach { artist ->
                put(JSONObject().apply {
                    put("id", artist.id)
                    put("name", artist.name)
                })
            }
        })
        song.album?.let { album ->
            obj.put("album", JSONObject().apply {
                put("id", album.id)
                put("title", album.title)
                put("artistName", album.artistName ?: "")
                put("coverUri", album.coverUri ?: "")
                put("year", album.year ?: 0)
            })
        }
        obj.put("bitrateKbps", song.format.bitrateKbps ?: 0)
        when (val loc = song.location) {
            is MediaLocation.Local -> obj.put(
                "location",
                JSONObject().apply {
                    put("type", "local")
                    put("uri", loc.uri)
                    put("filePath", loc.filePath ?: "")
                },
            )
            is MediaLocation.WebDav -> obj.put(
                "location",
                JSONObject().apply {
                    put("type", "webdav")
                    put("serverId", loc.serverId)
                    put("remotePath", loc.remotePath)
                },
            )
            is MediaLocation.Remote -> obj.put(
                "location",
                JSONObject().apply {
                    put("type", "remote")
                    put("sourceId", loc.sourceId)
                    put("songId", loc.songId)
                    put("payload", loc.payload ?: "")
                },
            )
        }
        return obj.toString()
    }

    fun fromJson(json: String): Song? = runCatching {
        val obj = JSONObject(json)
        val locationJson = obj.optJSONObject("location") ?: return@runCatching null
        val location = when (locationJson.optString("type")) {
            "local" -> MediaLocation.Local(
                uri = locationJson.optString("uri"),
                filePath = locationJson.optString("filePath").takeIf { it.isNotBlank() },
            )
            "webdav" -> MediaLocation.WebDav(
                serverId = locationJson.optString("serverId"),
                remotePath = locationJson.optString("remotePath"),
            )
            "remote" -> MediaLocation.Remote(
                sourceId = locationJson.optString("sourceId"),
                songId = locationJson.optString("songId"),
                payload = locationJson.optString("payload").takeIf { it.isNotBlank() },
            )
            else -> return@runCatching null
        }
        Song(
            id = obj.optString("id"),
            title = obj.optString("title"),
            artists = obj.optJSONArray("artists")?.objects()?.map { artist ->
                Artist(
                    id = artist.optString("id"),
                    name = artist.optString("name"),
                )
            } ?: emptyList(),
            album = obj.optJSONObject("album")?.let { album ->
                Album(
                    id = album.optString("id"),
                    title = album.optString("title"),
                    artistName = album.optString("artistName").takeIf { it.isNotBlank() },
                    coverUri = album.optString("coverUri").takeIf { it.isNotBlank() },
                    year = album.optInt("year").takeIf { it > 0 },
                )
            },
            durationMs = obj.optLong("durationMs"),
            coverUri = obj.optString("coverUri").takeIf { it.isNotBlank() },
            coverSeedColor = obj.optLong("coverSeedColor", 0xFF4F5B92),
            addedAt = obj.optLong("addedAt"),
            location = location,
            format = AudioFormat(
                bitrateKbps = obj.optInt("bitrateKbps").takeIf { it > 0 },
            ),
            metadataComplete = true,
        )
    }.getOrNull()

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }
}
