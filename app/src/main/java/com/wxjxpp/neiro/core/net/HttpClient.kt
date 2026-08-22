package com.wxjxpp.neiro.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * 极简 HTTP 客户端。
 *
 * 内置音源只需要"发一个请求、拿文本回来"，为此引入 OkHttp 并不值得，
 * 因此直接封装 HttpURLConnection，集中处理三件容易出错的事：
 * - gzip / deflate 解压（很多音乐接口默认压缩）
 * - 4xx/5xx 时要读 errorStream，否则拿不到接口给的错误信息
 * - 超时必须显式设置，否则默认无限等待
 */
class HttpClient(
    private val defaultUserAgent: String = DEFAULT_UA,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) {

    data class Response(
        val statusCode: Int,
        val body: String,
        val bytes: ByteArray,
        val headers: Map<String, String>,
    ) {
        val isSuccessful: Boolean get() = statusCode in 200..299
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Response = execute(url, "GET", null, headers)

    suspend fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
    ): Response = execute(
        url = url,
        method = "POST",
        body = json.toByteArray(Charsets.UTF_8),
        headers = headers + ("Content-Type" to "application/json"),
    )

    /** application/x-www-form-urlencoded 表单提交（网易云 weapi/eapi 用）。 */
    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val body = form.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
        return execute(
            url = url,
            method = "POST",
            body = body.toByteArray(Charsets.UTF_8),
            headers = headers + ("Content-Type" to "application/x-www-form-urlencoded"),
        )
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    suspend fun execute(
        url: String,
        method: String = "GET",
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
    ): Response = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method.uppercase()
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", headers["User-Agent"] ?: defaultUserAgent)
            setRequestProperty("Accept-Encoding", "gzip, deflate")
            headers.forEach { (k, v) -> if (!k.equals("User-Agent", true)) setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            body?.let { connection.outputStream.use { out -> out.write(it) } }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.decoded(connection.contentEncoding)?.use { it.readBytes() } ?: ByteArray(0)
            Response(
                statusCode = code,
                body = bytes.toString(Charsets.UTF_8),
                bytes = bytes,
                headers = connection.headerFields
                    .filterKeys { it != null }
                    .mapKeys { it.key!! }
                    .mapValues { it.value.joinToString("; ") },
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.decoded(encoding: String?): InputStream = when {
        encoding.equals("gzip", ignoreCase = true) -> GZIPInputStream(this)
        encoding.equals("deflate", ignoreCase = true) -> InflaterInputStream(this)
        else -> this
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}