package com.wxjxpp.neiro.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 大文件流式下载器。
 *
 * [HttpClient] 会把响应整体读进内存，适合接口 JSON；
 * 音频文件（几 MB 到几十 MB）必须边读边写盘，避免 OOM。
 */
object FileDownloader {
    /** 流式下载到输出流；返回写入字节数。非 2xx 抛异常。
     *  [onProgress] 每 64KB 回调一次（已读字节, 总字节；总字节未知时为 -1）。 */
    suspend fun download(
        url: String,
        sink: OutputStream,
        headers: Map<String, String> = emptyMap(),
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Long = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", headers["User-Agent"] ?: HttpClient.DEFAULT_UA)
            headers.forEach { (k, v) ->
                if (!k.equals("User-Agent", true)) setRequestProperty(k, v)
            }
        }
        try {
            if (connection.responseCode >= 400) {
                error("HTTP ${connection.responseCode}")
            }
            val totalBytes = connection.contentLengthLong
            var total = 0L
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    sink.write(buffer, 0, n)
                    total += n
                    onProgress(total, totalBytes)
                }
            }
            total
        } finally {
            connection.disconnect()
        }
    }
    /** 下载到本地文件（覆盖写）。 */
    suspend fun downloadToFile(
        url: String,
        target: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Long = FileOutputStream(target).use { download(url, it, headers, onProgress) }
}
}