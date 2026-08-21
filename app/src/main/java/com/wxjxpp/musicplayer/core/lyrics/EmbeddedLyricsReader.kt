package com.wxjxpp.musicplayer.core.lyrics

import android.content.ContentResolver
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * 内嵌歌词读取器。
 *
 * `MediaMetadataRetriever` 不暴露歌词字段，必须自己解标签。覆盖以下容器：
 *
 * | 容器            | 位置                                                        |
 * |-----------------|-------------------------------------------------------------|
 * | MP3 / AAC(ID3)  | ID3v2 `USLT`（非同步）/ `SYLT`（逐行同步）/ `TXXX:LYRICS`    |
 * | FLAC            | VORBIS_COMMENT：`LYRICS` / `UNSYNCEDLYRICS` / `LYRICS-XXX`   |
 * | MP4 / M4A       | `moov.udta.meta.ilst.©lyr`                                  |
 * | OGG / Opus      | VorbisComment / OpusTags                                    |
 *
 * 关键处理：
 * - ID3v2.2 / 2.3 / 2.4 三种版本的帧头长度与 size 编码都不同
 * - unsynchronisation（`FF 00` → `FF`）：整 tag 级（v2.3）与帧级（v2.4）都要还原
 * - v2.4 的 data length indicator（额外 4 字节）
 * - 同一文件可能有多个歌词帧：带时间戳的、逐字的优先，纯文本兜底
 * - 内嵌 TTML（部分工具塞进 USLT / `LYRICS` 里）能被识别并交给 TTML 解析器
 *
 * 用 ParcelFileDescriptor 拿 FileChannel 做随机读，content:// 也能定位文件尾部的
 * moov 盒，不依赖真实文件路径（Android 10+ 常常拿不到）。
 */
class EmbeddedLyricsReader(
    private val resolver: ContentResolver,
) {

    /**
     * 读到的歌词原文。
     *
     * [hint] 传给 [LyricsParserRegistry.parse] 用于选解析器；
     * 无法判断时为 null，由解析器自行嗅探。
     */
    data class Result(val content: String, val hint: String?)

    /** 读不到返回 null。不抛异常：解不出歌词不该影响播放。 */
    fun read(uriString: String): Result? = runCatching {
        val uri = Uri.parse(uriString)
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { input ->
                val channel = input.channel
                val candidates = buildList {
                    addAll(readId3v2(channel))
                    addAll(readFlac(channel))
                    addAll(readMp4(channel))
                    addAll(readOgg(channel))
                }
                pickBest(candidates)
            }
        }
    }.getOrNull()

    // ---- 候选择优 ----

    /**
     * 一个文件可能同时有纯文本歌词与带时间轴歌词，取"信息量最大"的那份。
     *
     * 排序依据：TTML > 逐字 LRC > 带行时间戳 LRC > 纯文本。
     */
    private fun pickBest(candidates: List<String>): Result? {
        val cleaned = candidates.mapNotNull { it.normalizeLyricsText() }
        if (cleaned.isEmpty()) return null
        val best = cleaned.maxByOrNull { score(it) } ?: return null
        return Result(best, hintOf(best))
    }

    private fun score(text: String): Int {
        var score = 0
        if (looksLikeTtml(text)) score += 8
        if (WORD_TAG.containsMatchIn(text)) score += 4
        if (LINE_TAG.containsMatchIn(text)) score += 2
        if (text.length > 40) score += 1
        return score
    }

    private fun hintOf(text: String): String? = when {
        looksLikeTtml(text) -> LyricsHints.TTML
        WORD_TAG.containsMatchIn(text) -> LyricsHints.ENHANCED_LRC
        LINE_TAG.containsMatchIn(text) -> LyricsHints.LRC
        else -> null
    }

    private fun looksLikeTtml(text: String): Boolean =
        text.contains("<tt", ignoreCase = true) && text.contains("<p", ignoreCase = true)

    /** 去 BOM、统一换行、去掉 UTF-16 解码残留的 NUL。 */
    private fun String.normalizeLyricsText(): String? = this
        .removePrefix("\uFEFF")
        .replace("\u0000", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
        .takeIf { it.isNotBlank() }

    // ---- ID3v2（MP3 / 部分 AAC、DSF） ----

    private fun readId3v2(channel: FileChannel): List<String> {
        if (channel.size() < 10) return emptyList()
        val header = channel.readAt(0, 10) ?: return emptyList()
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return emptyList()
        }
        val major = header[3].toInt() and 0xFF
        val flags = header[5].toInt() and 0xFF
        val tagSize = syncSafeInt(header, 6)
        if (tagSize <= 0) return emptyList()

        val body = channel.readAt(10, tagSize.coerceAtMost(MAX_TAG)) ?: return emptyList()
        // v2.3 的 unsynchronisation 作用于整个 tag
        val tag = if (major < 4 && flags and 0x80 != 0) body.deUnsynchronise() else body

        var offset = 0
        // 扩展头
        if (flags and 0x40 != 0 && tag.size > 6) {
            offset += if (major >= 4) syncSafeInt(tag, 0) else beInt(tag, 0) + 4
            if (offset < 0 || offset >= tag.size) return emptyList()
        }

        val idLength = if (major == 2) 3 else 4
        val headerLength = if (major == 2) 6 else 10
        val result = mutableListOf<String>()

        while (offset + headerLength <= tag.size) {
            val id = String(tag, offset, idLength, Charsets.ISO_8859_1)
            if (id.isBlank() || id[0] == '\u0000') break

            var size = when {
                major == 2 -> ((tag[offset + 3].toInt() and 0xFF) shl 16) or
                    ((tag[offset + 4].toInt() and 0xFF) shl 8) or
                    (tag[offset + 5].toInt() and 0xFF)

                major >= 4 -> syncSafeInt(tag, offset + 4)
                else -> beInt(tag, offset + 4)
            }
            if (size <= 0 || offset + headerLength + size > tag.size) break

            val frameFlags = if (major >= 3) {
                ((tag[offset + 8].toInt() and 0xFF) shl 8) or (tag[offset + 9].toInt() and 0xFF)
            } else {
                0
            }
            var bodyStart = offset + headerLength
            // v2.4：data length indicator 占 4 字节
            if (major >= 4 && frameFlags and 0x0001 != 0 && size > 4) {
                bodyStart += 4
                size -= 4
            }
            var frame = tag.copyOfRange(bodyStart, bodyStart + size)
            // v2.4：帧级 unsynchronisation
            if (major >= 4 && frameFlags and 0x0002 != 0) frame = frame.deUnsynchronise()
            // 压缩 / 加密帧不处理
            val skipped = major >= 3 && (frameFlags and 0x0008 != 0 || frameFlags and 0x0004 != 0)

            if (!skipped) {
                when (id) {
                    "USLT", "ULT" -> decodeUslt(frame)?.let { result += it }
                    "SYLT", "SLT" -> decodeSylt(frame)?.let { result += it }
                    "TXXX", "TXX" -> decodeTxxxLyrics(frame)?.let { result += it }
                }
            }
            offset = bodyStart + size
        }
        return result
    }

    /** USLT / ULT：encoding(1) + language(3) + 描述(以 null 结尾) + 歌词正文。 */
    private fun decodeUslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val encoding = body[0].toInt() and 0xFF
        val charset = id3Charset(encoding)
        var pos = 4 // encoding + language
        pos = skipTerminated(body, pos, encoding)
        if (pos >= body.size) return null
        return decode(body, pos, body.size - pos, encoding, charset)
    }

    /**
     * SYLT：逐行同步歌词。
     *
     * 结构：encoding(1) + language(3) + timestampFormat(1) + contentType(1) +
     * 描述(null 结尾)，之后是「文本 + null + 4 字节时间戳」的重复段。
     * timestampFormat==2 才是毫秒，==1 是 MPEG 帧号，无采样率无法换算。
     * 转成标准 LRC 交给解析器。
     */
    private fun decodeSylt(body: ByteArray): String? {
        if (body.size < 7) return null
        val encoding = body[0].toInt() and 0xFF
        val charset = id3Charset(encoding)
        if ((body[5].toInt() and 0xFF) != 2) return null
        var pos = 6
        pos = skipTerminated(body, pos, encoding)

        val builder = StringBuilder()
        val step = terminatorSize(encoding)
        while (pos + 4 < body.size) {
            val textEnd = findTerminator(body, pos, encoding)
            if (textEnd < 0) break
            val text = decode(body, pos, textEnd - pos, encoding, charset).orEmpty()
            pos = textEnd + step
            if (pos + 4 > body.size) break
            val ms = beInt(body, pos)
            pos += 4
            if (ms < 0) continue
            builder.append(formatLrcTime(ms.toLong()))
                .append(text.trim())
                .append('\n')
        }
        return builder.toString().takeIf { it.isNotBlank() }
    }

    /** TXXX：`描述 \u0000 内容`，只接受描述里含 LYRIC 的。 */
    private fun decodeTxxxLyrics(body: ByteArray): String? {
        if (body.size < 2) return null
        val encoding = body[0].toInt() and 0xFF
        val charset = id3Charset(encoding)
        var pos = 1
        val descEnd = findTerminator(body, pos, encoding)
        if (descEnd < 0) return null
        val description = decode(body, pos, descEnd - pos, encoding, charset).orEmpty()
        if (!description.contains("LYRIC", ignoreCase = true)) return null
        pos = descEnd + terminatorSize(encoding)
        if (pos >= body.size) return null
        return decode(body, pos, body.size - pos, encoding, charset)
    }

    // ---- FLAC ----

    private fun readFlac(channel: FileChannel): List<String> {
        val magic = channel.readAt(0, 4) ?: return emptyList()
        if (String(magic, Charsets.ISO_8859_1) != "fLaC") return emptyList()

        var offset = 4L
        var guard = 0
        while (offset + 4 <= channel.size() && guard++ < 64) {
            val header = channel.readAt(offset, 4) ?: return emptyList()
            val isLast = (header[0].toInt() and 0x80) != 0
            val blockType = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            offset += 4
            if (length < 0) return emptyList()

            // 4 = VORBIS_COMMENT
            if (blockType == 4) {
                val body = channel.readAt(offset, length.coerceAtMost(MAX_TAG)) ?: return emptyList()
                return parseVorbisComment(body)
            }
            if (isLast) return emptyList()
            offset += length
        }
        return emptyList()
    }

    /**
     * Vorbis Comment 结构（小端）：
     * vendorLength + vendor + commentCount + [length + "KEY=VALUE"] * count
     */
    private fun parseVorbisComment(body: ByteArray): List<String> {
        val buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.remaining() < 4) return emptyList()
        val vendorLength = buffer.int
        if (vendorLength < 0 || vendorLength > buffer.remaining()) return emptyList()
        buffer.position(buffer.position() + vendorLength)
        if (buffer.remaining() < 4) return emptyList()
        val count = buffer.int
        if (count <= 0) return emptyList()

        val result = mutableListOf<String>()
        repeat(count.coerceAtMost(2048)) {
            if (buffer.remaining() < 4) return result
            val length = buffer.int
            if (length < 0 || length > buffer.remaining()) return result
            val entry = ByteArray(length).also { buffer.get(it) }.toString(Charsets.UTF_8)
            val separator = entry.indexOf('=')
            if (separator > 0) {
                val key = entry.substring(0, separator).uppercase().trim()
                if (key.isLyricsKey()) result += entry.substring(separator + 1)
            }
        }
        return result
    }

    /** 各家写法不统一：LYRICS / UNSYNCEDLYRICS / LYRICS-ZH / TTMLLYRICS ... */
    private fun String.isLyricsKey(): Boolean {
        if (this == "LYRICIST") return false
        return this == "LYRICS" ||
            this == "LYRIC" ||
            this == "UNSYNCEDLYRICS" ||
            this == "UNSYNCED LYRICS" ||
            this == "SYNCEDLYRICS" ||
            this == "TTMLLYRICS" ||
            startsWith("LYRICS")
    }

    // ---- MP4 / M4A ----

    private fun readMp4(channel: FileChannel): List<String> {
        val moov = findAtom(channel, 0L, channel.size(), "moov") ?: return emptyList()
        val udta = findAtom(channel, moov.first, moov.second, "udta") ?: return emptyList()
        val meta = findAtom(channel, udta.first, udta.second, "meta") ?: return emptyList()
        // meta 内容前 4 字节是 version/flags
        val ilst = findAtom(channel, meta.first + 4, meta.second, "ilst")
            ?: findAtom(channel, meta.first, meta.second, "ilst")
            ?: return emptyList()

        val result = mutableListOf<String>()
        listOf("\u00A9lyr", "lyr ", "----").forEach { type ->
            val atom = findAtom(channel, ilst.first, ilst.second, type) ?: return@forEach
            val data = findAtom(channel, atom.first, atom.second, "data") ?: return@forEach
            // data 内容前 8 字节是 version/flags + locale
            val textStart = data.first + 8
            val length = (data.second - textStart).toInt()
            if (length <= 0) return@forEach
            channel.readAt(textStart, length.coerceAtMost(MAX_TAG))
                ?.toString(Charsets.UTF_8)
                ?.let { result += it }
        }
        return result
    }

    /** 在 [start, end) 内找指定 atom，返回其内容区 [起始, 结束)。 */
    private fun findAtom(channel: FileChannel, start: Long, end: Long, type: String): Pair<Long, Long>? {
        var offset = start
        var guard = 0
        while (offset + 8 <= end && guard++ < 4096) {
            val header = channel.readAt(offset, 8) ?: return null
            var size = ((header[0].toInt() and 0xFF).toLong() shl 24) or
                ((header[1].toInt() and 0xFF).toLong() shl 16) or
                ((header[2].toInt() and 0xFF).toLong() shl 8) or
                (header[3].toInt() and 0xFF).toLong()
            val name = String(header, 4, 4, Charsets.ISO_8859_1)
            var contentStart = offset + 8

            when (size) {
                // 1 表示使用 64 位长度
                1L -> {
                    val ext = channel.readAt(offset + 8, 8) ?: return null
                    size = ByteBuffer.wrap(ext).order(ByteOrder.BIG_ENDIAN).long
                    contentStart = offset + 16
                }
                // 0 表示延伸到末尾
                0L -> size = end - offset
            }
            if (size < 8) return null
            if (name == type) return contentStart to (offset + size).coerceAtMost(end)
            offset += size
        }
        return null
    }

    // ---- OGG / Opus ----

    /**
     * Ogg 把 Vorbis/Opus 的 comment header 放在起始几页里。
     * 这里在文件头部有限范围内定位 `\u0003vorbis` 或 `OpusTags` 标记后直接解析，
     * 覆盖绝大多数实际文件（comment header 跨页的极端情况不处理）。
     */
    private fun readOgg(channel: FileChannel): List<String> {
        val magic = channel.readAt(0, 4) ?: return emptyList()
        if (String(magic, Charsets.ISO_8859_1) != "OggS") return emptyList()
        val head = channel.readAt(0, OGG_SCAN_BYTES.coerceAtMost(channel.size().toInt())) ?: return emptyList()

        indexOf(head, "\u0003vorbis".toByteArray(Charsets.ISO_8859_1)).takeIf { it >= 0 }?.let { index ->
            return parseVorbisComment(head.copyOfRange(index + 7, head.size))
        }
        indexOf(head, "OpusTags".toByteArray(Charsets.ISO_8859_1)).takeIf { it >= 0 }?.let { index ->
            return parseVorbisComment(head.copyOfRange(index + 8, head.size))
        }
        return emptyList()
    }

    // ---- 字节与编码工具 ----

    private fun id3Charset(encoding: Int) = when (encoding) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16 // 带 BOM
        2 -> Charsets.UTF_16BE
        else -> Charsets.UTF_8
    }

    private fun terminatorSize(encoding: Int): Int = if (encoding == 1 || encoding == 2) 2 else 1

    private fun findTerminator(body: ByteArray, from: Int, encoding: Int): Int {
        val step = terminatorSize(encoding)
        var pos = from
        while (pos + step <= body.size) {
            val hit = if (step == 1) {
                body[pos] == 0.toByte()
            } else {
                body[pos] == 0.toByte() && body[pos + 1] == 0.toByte()
            }
            if (hit) return pos
            pos += step
        }
        return -1
    }

    private fun skipTerminated(body: ByteArray, from: Int, encoding: Int): Int {
        val end = findTerminator(body, from, encoding)
        return if (end < 0) body.size else end + terminatorSize(encoding)
    }

    /**
     * ISO-8859-1 常被用来"装"实际是 UTF-8 的中文文本（很多打标工具的老 bug）。
     * 因此按 latin1 解出来若出现明显乱码，就再按 UTF-8 试一次。
     */
    private fun decode(
        body: ByteArray,
        offset: Int,
        length: Int,
        encoding: Int,
        charset: java.nio.charset.Charset,
    ): String? {
        if (length <= 0 || offset < 0 || offset + length > body.size) return null
        val primary = String(body, offset, length, charset)
        if (encoding != 0) return primary
        val bytes = body.copyOfRange(offset, offset + length)
        if (!looksLikeUtf8(bytes)) return primary
        return String(bytes, Charsets.UTF_8)
    }

    /** 严格校验 UTF-8 多字节序列，避免把真正的 latin1 文本误判。 */
    private fun looksLikeUtf8(bytes: ByteArray): Boolean {
        var i = 0
        var multiByte = false
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val extra = when {
                b < 0x80 -> 0
                b in 0xC2..0xDF -> 1
                b in 0xE0..0xEF -> 2
                b in 0xF0..0xF4 -> 3
                else -> return false
            }
            if (extra > 0) {
                multiByte = true
                if (i + extra >= bytes.size) return false
                for (j in 1..extra) {
                    if ((bytes[i + j].toInt() and 0xC0) != 0x80) return false
                }
            }
            i += extra + 1
        }
        return multiByte
    }

    /** unsynchronisation 还原：`FF 00` → `FF`。 */
    private fun ByteArray.deUnsynchronise(): ByteArray {
        val out = ByteArray(size)
        var written = 0
        var i = 0
        while (i < size) {
            out[written++] = this[i]
            if (this[i] == 0xFF.toByte() && i + 1 < size && this[i + 1] == 0.toByte()) i++
            i++
        }
        return out.copyOf(written)
    }

    private fun formatLrcTime(ms: Long): String {
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1000
        val centis = (ms % 1000) / 10
        return "[%02d:%02d.%02d]".format(minutes, seconds, centis)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun FileChannel.readAt(position: Long, length: Int): ByteArray? {
        if (length <= 0 || position < 0 || position >= size()) return null
        val actual = length.toLong().coerceAtMost(size() - position).toInt()
        val buffer = ByteBuffer.allocate(actual)
        var read = 0
        while (read < actual) {
            val n = read(buffer, position + read)
            if (n <= 0) break
            read += n
        }
        return if (read <= 0) null else buffer.array().copyOf(read)
    }

    private fun beInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    /** ID3 syncsafe 整数：每字节只用低 7 位。 */
    private fun syncSafeInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)

    private companion object {
        /** 单个标签块上限，防止畸形文件吃满内存。 */
        const val MAX_TAG = 4 * 1024 * 1024

        /** Ogg 头部扫描范围。 */
        const val OGG_SCAN_BYTES = 256 * 1024

        val LINE_TAG = Regex("""\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]|\[\d+,\d+]""")
        val WORD_TAG = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>|<\d+,\d+(?:,\d+)?>|\(\d+,\d+(?:,\d+)?\)""")
    }
}