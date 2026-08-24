package com.wxjxpp.neiro.core.download

import android.content.Context
import android.os.Environment
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.net.FileDownloader
import com.wxjxpp.neiro.core.player.DownloadNotifier
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
 * - 进度可见化：开始/结束都发系统通知（[DownloadNotifier]），
 *   并通过 [onEvent] 回调给应用内横幅展示
 */
class DownloadManager(
    private val context: Context,
    private val registry: MusicSourceRegistry,
) {
    /** 下载事件回调：(歌曲标题, 是否结束, 用户可读消息)。由 ViewModel 转成横幅。 */
    var onEvent: ((String, Boolean, String) -> Unit)? = null

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

    /** 通知槽位：同一首歌的开始/完成通知共用一个 id，避免通知堆积。 */
    private fun notifySlot(songId: String): Int = songId.hashCode().mod(1000)

    /** 下载歌曲文件。返回用户可读的结果消息，同时发通知 + 事件回调。 */
    suspend fun downloadSong(song: Song): String = withContext(Dispatchers.IO) {
        val slot = notifySlot(song.id)
        DownloadNotifier.showStart(context, song.title, slot)
        onEvent?.invoke(song.title, false, "开始下载「${song.title}」，请在通知栏查看进度")
        val url = resolveUrl(song).getOrElse {
            val msg = "下载失败：${it.message}"
            DownloadNotifier.showDone(context, "下载失败", "${song.title} · $msg", slot)
            onEvent?.invoke(song.title, true, msg)
            return@withContext msg
        }
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
            onSuccess = { path ->
                val fileName = path.substringAfterLast('/')
                val msg = "下载完成「$fileName」，请到 Music/Neiro 查看"
                DownloadNotifier.showDone(context, "下载完成", "$msg\n完整路径：$path", slot)
                onEvent?.invoke(song.title, true, msg)
                path
            },
            onFailure = {
                val msg = "下载失败：${it.message ?: "未知错误"}"
                DownloadNotifier.showDone(context, "下载失败", "${song.title} · $msg", slot)
                onEvent?.invoke(song.title, true, msg)
                msg
            },
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
            onSuccess = { path ->
                val msg = "歌词已保存到 Documents/Neiro/lyrics"
                onEvent?.invoke(song.title, true, msg)
                path
            },
            onFailure = {
                val msg = "歌词下载失败：${it.message ?: "未知错误"}"
                onEvent?.invoke(song.title, true, msg)
                msg
            },
        )
    }

    /** 文件名安全化 + 扩展名。 */
    private fun safeFileName(title: String, artist: String, ext: String): String {
val raw = "$artist - $title"
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        return "$cleaned.$ext"
    }
}