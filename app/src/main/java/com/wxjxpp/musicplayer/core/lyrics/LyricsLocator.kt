package com.wxjxpp.musicplayer.core.lyrics

import android.content.ContentResolver
import android.net.Uri
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.MediaLocation
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.scanner.MetadataReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 歌词来源查找。
 *
 * 优先级：内嵌歌词 → 同名外挂文件（.lrc/.ttml/.srt/.txt）→ 空。
 * 在线音源歌词由 MusicSource 提供，不在这里处理。
 */
class LyricsLocator(
    private val resolver: ContentResolver,
    private val parsers: LyricsParserRegistry,
    private val metadataReader: MetadataReader,
) {

    private val extensions = listOf("lrc", "ttml", "srt", "txt")

    suspend fun find(song: Song): Lyrics = withContext(Dispatchers.IO) {
        metadataReader.readEmbeddedLyrics(song)?.takeIf { !it.isEmpty }?.let { return@withContext it }
        findSidecar(song) ?: Lyrics.Empty
    }

    /** 找同名外挂歌词文件。只有能拿到真实文件路径时才可行。 */
    private fun findSidecar(song: Song): Lyrics? {
        val local = song.location as? MediaLocation.Local ?: return null
        val path = local.filePath?.takeIf { !it.startsWith("content://") }
            ?: resolvePath(local.uri)
            ?: return null
        val base = File(path)
        if (!base.exists()) return null
        val stem = base.nameWithoutExtension
        val dir = base.parentFile ?: return null

        for (ext in extensions) {
            val candidate = File(dir, "$stem.$ext")
            if (candidate.isFile) {
                val text = runCatching { candidate.readText() }.getOrNull() ?: continue
                val parsed = parsers.parse(text, hint = candidate.name)
                if (!parsed.isEmpty) return parsed
            }
        }
        return null
    }

    /** content:// 反查真实路径，失败返回 null（Android 10+ 常见）。 */
    private fun resolvePath(uriString: String): String? = runCatching {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") return uri.path
        resolver.query(uri, arrayOf(android.provider.MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}