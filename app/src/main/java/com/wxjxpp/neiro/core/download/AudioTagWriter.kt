package com.wxjxpp.neiro.core.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.wxjxpp.neiro.core.model.Song
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File

/**
 * 下载完成后的元数据嵌入：标题/歌手/专辑 + 专辑封面 + LRC 歌词。
 *
 * JAudiotagger 按扩展名自动选择标签方案：
 * - mp3 → ID3v2（USLT 歌词帧）
 * - m4a/mp4 → MP4 tag（©lyr）
 * - flac/ogg → Vorbis Comment（LYRICS）
 */
object AudioTagWriter {

    /** 把下载好的音频文件写入完整标签。coverBytes/lyricText 任一为空则跳过对应项。 */
    fun embed(
        file: File,
        song: Song,
        coverBytes: ByteArray?,
        coverMime: String?,
        lyricLrc: String?,
    ) {
        val af = AudioFileIO.read(file)
        val tag = af.tagOrCreateDefault
        tag.setField(FieldKey.TITLE, song.title)
        if (song.artistName.isNotBlank()) tag.setField(FieldKey.ARTIST, song.artistName)
        song.albumTitle?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ALBUM, it) }
        coverBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            val art = AndroidArtwork()
            art.binaryData = bytes
            art.mimeType = coverMime ?: "image/jpeg"
            art.width = 1024
            art.height = 1024
            tag.deleteArtworkField()
            tag.addField(art)
        }
        lyricLrc?.takeIf { it.isNotBlank() }?.let { lrc -> setLyrics(tag, lrc) }
        af.commit()
    }

    /** 各容器格式的歌词字段写入。失败静默（歌词属增强项，不因它让下载报错）。
     *  FieldKey.LYRICS 在 ID3 自动落 USLT 帧、MP4 落 ©lyr、Vorbis 落 LYRICS。 */
    private fun setLyrics(tag: org.jaudiotagger.tag.Tag, lrc: String) {
        runCatching { tag.setField(FieldKey.LYRICS, lrc) }
    }

    /** SAF：把 File 复制进用户选择的目录，返回显示名；失败抛异常由调用方兜底。 */
    fun copyIntoSafDir(context: Context, dirUri: String, file: File, displayName: String): String {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(dirUri))
            ?: error("下载目录不可用，请在设置中重新选择")
        val mime = when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "mp4" -> "audio/mp4"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "audio/ogg"
            else -> "application/octet-stream"
        }
        var target = tree.findFile(displayName)
        if (target != null && target.exists()) {
            // 同名覆盖写
            val out = context.contentResolver.openOutputStream(target.uri, "wt")
                ?: error("无法写入同名文件")
            out.use { it.write(file.readBytes()) }
            return displayName
        }
        target = tree.createFile(mime, displayName.removeSuffix(".${file.extension}"))
            ?: error("创建下载文件失败")
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: error("无法打开输出流")
        return displayName
    }

    /** 通过 SAF 读任意文档字节（取歌词/封面缓存用）。 */
    fun readBytes(context: Context, uri: Uri): ByteArray? =
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

    /** DocumentFile 便捷查询。 */
    fun documentFile(context: Context, uri: Uri): DocumentFile? =
        runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
}
