package com.wxjxpp.musicplayer.core.scanner

import com.wxjxpp.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * 本地歌曲扫描器契约。
 *
 * 实现方式可以是 MediaStore、SAF 目录遍历，或两者混合，
 * 业务层只消费 [scan] 吐出的进度流。
 */
interface MediaScanner {
    /** 扫描指定目录（为空表示全盘/MediaStore）。以流的形式上报进度。 */
    fun scan(roots: List<String> = emptyList()): Flow<ScanProgress>
}

sealed interface ScanProgress {
    data object Started : ScanProgress
    data class Found(val song: Song, val scanned: Int, val total: Int?) : ScanProgress
    data class Failed(val path: String, val reason: String) : ScanProgress
    data class Completed(val total: Int, val elapsedMs: Long) : ScanProgress
}

/**
 * 元数据读取器契约。
 *
 * 扫描时先建轻量索引，再由它异步补齐标签、封面、ReplayGain。
 * 内嵌歌词不在这里：由 core/lyrics/EmbeddedLyricsReader 直接解标签，
 * 结果经 LyricsLocator 汇总，避免同一份数据两处解析。
 */
interface MetadataReader {
    /** 读取标签，返回补齐后的 Song。读取失败应返回原对象而不是抛异常。 */
    suspend fun readMetadata(song: Song): Song

    /** 取封面原始字节。没有内嵌封面时返回 null。 */
    suspend fun readArtwork(song: Song): ByteArray?
}