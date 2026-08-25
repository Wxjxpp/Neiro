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
import kotlinx.coroutines.flow.first
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
    private val settings: com.wxjxpp.neiro.core.data.DataStoreSettingsRepository,
    private val httpClient: com.wxjxpp.neiro.core.net.HttpClient,
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
    /** 下载歌曲文件。返回用户可读的结果消息，同时发通知 + 事件回调。
     *  流程：直链下载到缓存 → 按设置嵌入标题/歌手/专辑/封面/歌词 → 落位（自定义目录或公共音乐）。 */
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
        // 1) 先落到缓存临时文件（便于嵌入标签后再搬运）
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
            .takeIf { it in setOf("mp3", "flac", "m4a", "ogg", "opus", "wav") } ?: "mp3"
        val tmp = File(context.cacheDir, "neiro_dl_${System.currentTimeMillis()}.$ext")
        val downloaded = runCatching { FileDownloader.downloadToFile(url, tmp) > 0L }
            .getOrElse { false }
        if (!downloaded) {
            tmp.delete()
            val msg = "下载失败：服务器返回空内容"
            DownloadNotifier.showDone(context, "下载失败", "${song.title} · $msg", slot)
            onEvent?.invoke(song.title, true, msg)
            return@withContext msg
        }
        // 2) 元数据嵌入（失败不阻断下载，只跳过增强项；失败原因附到结果消息便于排查）
        var embedNote = ""
        var embedIssue = ""
        runCatching {
            val embedCover = settings.downloadEmbedCover.first()
            val embedLyrics = settings.downloadEmbedLyrics.first()
            if (!embedCover && !embedLyrics) return@runCatching
            val cover = if (embedCover) fetchCoverBytes(song) else null
            if (embedCover && cover == null) embedIssue += "封面未获取到 "
            val lyric = if (embedLyrics) fetchLyricsLrc(song) else null
            if (embedLyrics && lyric == null) embedIssue += "歌词未获取到 "
            AudioTagWriter.embed(tmp, song, cover, null, lyric)
            embedNote = listOfNotNull(
                if (cover != null) "封面" else null,
                if (lyric != null) "歌词" else null,
            ).joinToString("、").let {
                when {
                    it.isNotEmpty() -> "（已嵌入$it）"
                    embedIssue.isNotBlank() -> "（${embedIssue.trim()}）"
                    else -> ""
                }
            }
        }.onFailure { e ->
            android.util.Log.w("DownloadManager", "元数据嵌入失败: ${song.title}", e)
            embedNote = "（标签嵌入失败：${e.message?.take(40)}）"
        }
        // 3) 落位：自定义 SAF 目录优先，否则公共 Music/Neiro
        val dirUri = settings.observeDownloadDirUri().first()
        val result = runCatching {
            if (dirUri.isNotBlank()) {
                val name = safeFileName(song.title, song.artistName, ext)
                AudioTagWriter.copyIntoSafDir(context, dirUri, tmp, name)
                tmp.delete()
                name
            } else {
                val musicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "Neiro",
                ).apply { mkdirs() }
                val target = File(musicDir, safeFileName(song.title, song.artistName, ext))
                tmp.renameTo(target) || runCatching {
                    tmp.copyTo(target, overwrite = true); tmp.delete(); true
                }.getOrDefault(false)
                target.absolutePath
            }
        }
        result.fold(
            onSuccess = { path ->
                val fileName = path.substringAfterLast('/')
                val msg = "下载完成「$fileName」$embedNote"
                DownloadNotifier.showDone(context, "下载完成", "$msg\n位置：$path", slot)
                onEvent?.invoke(song.title, true, msg)
                path
            },
            onFailure = {
                tmp.delete()
                val msg = "下载失败：${it.message ?: "未知错误"}"
                DownloadNotifier.showDone(context, "下载失败", "${song.title} · $msg", slot)
                onEvent?.invoke(song.title, true, msg)
                msg
            },
        )
    }

    /** 抓取专辑图字节：http(s) 直取；content:// 走 ContentResolver；song 无封面时回退专辑封面。 */
    private suspend fun fetchCoverBytes(song: Song): ByteArray? {
        val uri = song.coverUri.orEmpty().ifEmpty { song.album?.coverUri.orEmpty() }
        if (uri.isEmpty()) return null
        return runCatching {
            when {
                uri.startsWith("http") ->
                    httpClient.execute(uri).bytes.takeIf { it.isNotEmpty() }
                uri.startsWith("content") ->
                    context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { it.readBytes() }
                else -> null
            }
        }.getOrNull()?.takeIf { it.size > 1024 } // 过滤占位小图
    }

    /** 抓取 LRC 歌词文本：本机音源优先，其余在线音源兜底。 */
    private suspend fun fetchLyricsLrc(song: Song): String? {
        val candidates = registry.sources.filterIsInstance<com.wxjxpp.neiro.core.source.OnlineMusicSource>()
        for (source in candidates) {
            val raw = runCatching { source.fetchLyricsRaw(song) }.getOrNull() ?: continue
            if (raw.content.isNotBlank()) return raw.content
        }
        return null
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