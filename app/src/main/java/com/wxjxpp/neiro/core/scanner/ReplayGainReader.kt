package com.wxjxpp.neiro.core.scanner

import android.content.ContentResolver
import android.net.Uri
import com.wxjxpp.neiro.core.model.ReplayGain
import java.io.FileInputStream
import java.nio.charset.Charset
/** 从常见标签读取 ReplayGain；读取失败返回空对象，不影响扫描和播放。 */
class ReplayGainReader(private val resolver: ContentResolver) {
    fun read(uriString: String): ReplayGain = runCatching {
        resolver.openFileDescriptor(Uri.parse(uriString), "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { input ->
                // FLAC/Vorbis comments、ID3 TXXX 通常都在文件头；限制大小避免大文件占内存。
                val bytes = ByteArray(MAX_BYTES)
                var size = 0
                while (size < bytes.size) {
                    val n = input.read(bytes, size, bytes.size - size)
                    if (n <= 0) break
                    size += n
                }
                parse(bytes.copyOf(size))
            }
        } ?: ReplayGain()
    }.getOrDefault(ReplayGain())

    private fun parse(bytes: ByteArray): ReplayGain {
        val candidates = linkedSetOf<String>()
        candidates += decode(bytes, Charsets.UTF_8)
        candidates += decode(bytes, Charsets.ISO_8859_1)
        candidates += decode(bytes, Charsets.UTF_16LE)
        candidates += decode(bytes, Charsets.UTF_16BE)
        fun value(name: String): Float? {
            val regex = Regex("(?im)(?:^|[\\u0000\\n\\r;])(?:TXXX:)?$name\\s*=\\s*([-+]?\\d+(?:[.,]\\d+)?)")
            val txxxRegex = Regex("(?im)(?:^|[\\u0000\\n\\r])$name\\s*[=\\u0000\\n\\r]+\\s*([-+]?\\d+(?:[.,]\\d+)?)")
            return candidates.asSequence().mapNotNull { text ->
                regex.find(text)?.groupValues?.get(1)
                    ?: txxxRegex.find(text)?.groupValues?.get(1)
            }.mapNotNull { it.replace(',', '.').toFloatOrNull() }.firstOrNull()
        }
        return ReplayGain(
            trackGainDb = value("REPLAYGAIN_TRACK_GAIN"),
            trackPeak = value("REPLAYGAIN_TRACK_PEAK"),
            albumGainDb = value("REPLAYGAIN_ALBUM_GAIN"),
            albumPeak = value("REPLAYGAIN_ALBUM_PEAK"),
        )
    }

    private fun decode(bytes: ByteArray, charset: Charset): String =
        String(bytes, charset).replace('\u0000', '\n')

    private companion object { const val MAX_BYTES = 512 * 1024 }
}
