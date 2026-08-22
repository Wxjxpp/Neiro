package com.wxjxpp.neiro.core.download

import android.content.Context
import android.os.Environment
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.net.FileDownloader
import com.wxjxpp.neiro.core.source.OnlineMusicSource
import com.wxjxpp.neiro.core.source.MusicSourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 在线歌曲下载。
 *
 * - 歌曲：先解析直链再流式落盘到 Music/Neiro（公共目录，用户可直接在
 *   其他播放器中看到；Android 10+ 无需存储权限）
 * - 歌词：从音源拉 LRC 原文存到 Documents/Neiro/lyrics
 */
class DownloadManager(
    private val context: Context,
    private val registry: MusicSourceRegistry,
) {

    /** 解析直链（复用播放链路），音质逐级降级尝试。 */
    private suspend fun resolveUrl(song: Song): Result<String> {
        val remote = song.location as? MediaLocation.Remote
            ?: return Result.failure(IllegalStateException("这不是在线歌曲"))
        val source = registry.find(remote.sourceId) as? OnlineMusicSource
            ?: return Result.failure(IllegalStateException("找不到音源：${remote.sourceId}"))
        // 下载按无损 → 高 → 标准逐级降级，脚本不支持高档时自动回退
        val qualities = listOf(
            com.wxjxpp.neiro.core.model.Quality.Lossless,
            com.wxjxpp.neiro.core.model.Quality.High,
            com.wxjxpp.neiro.core.model.Quality.Standard,
        )
        var lastReason = "未知错误"
        for (quality in qualities) {
            when (val result = source.resolvePlayUrlDetailed(song, quality)) {
                is OnlineMusicSource.PlayUrlResult.Success -> return Result.success(result.url)
                is OnlineMusicSource.PlayUrlResult.Failure -> lastReason = result.reason
            }
        }
        return Result.failure(IllegalStateException(lastReason))
    }

    /** 下载歌曲文件。返回用户可读的结果消息。 */
    suspend fun downloadSong(song: Song): String = withContext(Dispatchers.IO) {
        val url = resolveUrl(song).getOrElse { return@withContext "下载失败：${it.message}" }
        runCatching {
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "Neiro",
            ).apply { mkdirs() }
            val target = File(musicDir, safeFileName(song.title, song.artistName, "mp3"))
            val bytes = FileDownloader.downloadToFile(url, target)
            if (bytes <= 0L) error("服务器返回空内容")
            target.absolutePath
        }.fold(
            onSuccess = { path -> "已下载：$path" },
            onFailure = { "下载失败：${it.message ?: "未知错误"}" },
        )
    }

    /** 下载歌词（LRC）。 */
    suspend fun downloadLyrics(song: Song): String = withContext(Dispatchers.IO) {
        val remote = song.location as? MediaLocation.Remote
            ?: return@withContext "下载失败：这不是在线歌曲"
        val source = registry.find(remote.sourceId) as? OnlineMusicSource
            ?: return@withContext "下载失败：找不到音源"
        runCatching {
            val raw = source.fetchLyricsRaw(song)
                ?: error("该平台没有返回歌词")
            val lyricsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "Neiro/lyrics",
            ).apply { mkdirs() }
            val target = File(lyricsDir, safeFileName(song.title, song.artistName, "lrc"))
            target.writeText(raw.content)
            target.absolutePath
        }.fold(
            onSuccess = { path -> "歌词已保存：$path" },
            onFailure = { "歌词下载失败：${it.message ?: "未知错误"}" },
        )
    }

    /** 文件名安全化 + 扩展名。 */
    private fun safeFileName(title: String, artist: String, ext: String): String {
        val raw = "$artist - $title"
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        return "$cleaned.$ext"
    }
}