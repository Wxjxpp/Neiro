package com.wxjxpp.musicplayer.core.lyrics

import android.content.ContentResolver
import android.net.Uri
import com.wxjxpp.musicplayer.core.model.Lyrics
import com.wxjxpp.musicplayer.core.model.MediaLocation
import com.wxjxpp.musicplayer.core.model.Song
import com.wxjxpp.musicplayer.core.source.MusicSource
import com.wxjxpp.musicplayer.core.source.MusicSourceRegistry
import com.wxjxpp.musicplayer.core.source.SourceCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 歌词来源查找。
 *
 * 优先级：
 * 1. 文件内嵌歌词（USLT / SYLT / VorbisComment / ©lyr，含 TTML）
 * 2. 同名外挂文件（.lrc / .ttml / .srt / .qrc / .krc / .vtt / .txt），
 *    并尝试合并同名翻译 / 罗马音文件
 * 3. 在线音源歌词（在线歌曲；本地歌曲若开启则按"歌名 + 歌手"匹配）
 *
 * [sourceRegistry] 用 lambda 传入是为了打破装配顺序上的循环依赖：
 * 音源注册表本身依赖曲库仓库，而曲库仓库又依赖本类。
 */
class LyricsLocator(
    private val resolver: ContentResolver,
    private val parsers: LyricsParserRegistry,
    private val embeddedReader: EmbeddedLyricsReader,
    private val sourceRegistry: () -> MusicSourceRegistry? = { null },
) {

    private companion object {
        /** 主歌词候选扩展名，按优先级排列：信息量高的在前。 */
        val MAIN_EXTENSIONS = listOf("ttml", "lrc", "qrc", "krc", "srt", "vtt", "txt")

        /** 翻译外挂文件的常见后缀，例如 `song.zh.lrc`、`song.trans.lrc`。 */
        val TRANSLATION_SUFFIXES = listOf("zh", "zh-cn", "zh_cn", "chs", "cn", "trans", "translation", "tr")

        /** 罗马音外挂文件后缀。 */
        val ROMANIZATION_SUFFIXES = listOf("romaji", "roman", "rm", "ro")
    }

    suspend fun find(song: Song): Lyrics = withContext(Dispatchers.IO) {
        when (song.location) {
            is MediaLocation.Local -> findLocal(song)
            // 在线歌曲直接问音源
            else -> fetchFromSources(song) ?: Lyrics.Empty
        }
    }

    private suspend fun findLocal(song: Song): Lyrics {
        readEmbedded(song)?.let { return it }
        findSidecar(song)?.let { return it }
        return fetchFromSources(song) ?: Lyrics.Empty
    }

    /** 内嵌歌词。读取器给出格式提示，交给对应解析器。 */
    private fun readEmbedded(song: Song): Lyrics? {
        val local = song.location as? MediaLocation.Local ?: return null
        val result = embeddedReader.read(local.uri)
            ?: local.filePath?.let { path -> embeddedReader.read(Uri.fromFile(File(path)).toString()) }
            ?: return null
        return parsers.parse(result.content, hint = result.hint).takeIf { !it.isEmpty }
    }

    /** 找同名外挂歌词文件。只有能拿到真实文件路径时才可行。 */
    private fun findSidecar(song: Song): Lyrics? {
        val local = song.location as? MediaLocation.Local ?: return null
        val path = local.filePath?.takeIf { !it.startsWith("content://") }
            ?: resolvePath(local.uri)
            ?: return null
        val base = File(path)
        val dir = base.parentFile ?: return null
        if (!dir.isDirectory) return null
        val stem = base.nameWithoutExtension

        for (ext in MAIN_EXTENSIONS) {
            val candidate = File(dir, "$stem.$ext")
            if (!candidate.isFile) continue
            val text = candidate.readTextOrNull() ?: continue
            val main = parsers.parse(text, hint = candidate.name)
            if (main.isEmpty) continue
            // TTML 自带翻译，不需要再找外挂译文
            if (main.hasTranslation && main.hasRomanization) return main
            return parsers.merge(
                main = main,
                translation = main.takeIf { !it.hasTranslation }
                    ?.let { readSidecarVariant(dir, stem, TRANSLATION_SUFFIXES) },
                romanization = main.takeIf { !it.hasRomanization }
                    ?.let { readSidecarVariant(dir, stem, ROMANIZATION_SUFFIXES) },
            )
        }
        return null
    }

    /** 读取 `song.zh.lrc` 这类附属歌词文件。 */
    private fun readSidecarVariant(dir: File, stem: String, suffixes: List<String>): Lyrics? {
        for (suffix in suffixes) {
            for (ext in listOf("lrc", "txt")) {
                val file = File(dir, "$stem.$suffix.$ext")
                if (!file.isFile) continue
                val text = file.readTextOrNull() ?: continue
                val parsed = parsers.parse(text, hint = file.name)
                if (!parsed.isEmpty) return parsed
            }
        }
        return null
    }

    /**
     * 问在线音源要歌词。
     *
     * 在线歌曲交给它自己的音源；本地歌曲则遍历所有声明 [SourceCapability.Lyrics]
     * 的在线音源，命中即用（自定义音源可能只支持部分平台）。
     */
    private suspend fun fetchFromSources(song: Song): Lyrics? {
        val registry = sourceRegistry() ?: return null
        val candidates = when (val location = song.location) {
            is MediaLocation.Remote -> listOfNotNull(
                registry.find(location.sourceId)
                    ?: registry.sources.firstOrNull { it.id.endsWith(":${location.sourceId}") }
            ).ifEmpty { registry.sourcesWith(SourceCapability.Lyrics) }

            // 本地歌曲兜底：能按歌名匹配到在线歌词也算收获
            else -> registry.sourcesWith(SourceCapability.Lyrics)
                .filterNot { SourceCapability.Offline in it.capabilities }
        }

        for (source in candidates) {
            fetchFrom(source, song)?.let { return it }
        }
        return null
    }

    private suspend fun fetchFrom(source: MusicSource, song: Song): Lyrics? {
        runCatching { source.fetchLyrics(song) }.getOrNull()?.takeIf { !it.isEmpty }?.let { return it }
        val raw = runCatching { source.fetchLyricsRaw(song) }.getOrNull() ?: return null
        val parsed = parsers.parseRemote(
            lyric = raw.content,
            translation = raw.translationContent,
            romanization = raw.romanizationContent,
            wordByWord = raw.wordByWordContent,
        )
        return parsed.takeIf { !it.isEmpty }
    }

    /** content:// 反查真实路径，失败返回 null（Android 10+ 常见）。 */
    private fun resolvePath(uriString: String): String? = runCatching {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") return uri.path
        resolver.query(uri, arrayOf(android.provider.MediaStore.Audio.Media.DATA), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /** 外挂歌词文件编码不统一：UTF-8 解不出可读文本时退回 GBK。 */
    private fun File.readTextOrNull(): String? = runCatching {
        val bytes = readBytes()
        if (bytes.isEmpty()) return null
        val utf8 = bytes.toString(Charsets.UTF_8)
        // U+FFFD 是解码失败的替换字符，出现即说明不是 UTF-8
        if (!utf8.contains('\uFFFD')) return utf8
        String(bytes, charset("GBK"))
    }.getOrNull()
}