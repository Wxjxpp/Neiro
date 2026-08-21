package com.wxjxpp.musicplayer.core.userapi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 代脚本发起 HTTP 请求。
 *
 * 脚本自身不能直接联网：它通过 `request` 动作把 URL/方法/头交给宿主，
 * 宿主发完再用 [UserApiEngine.sendHttpResponse] 回填。
 * 这样网络行为始终在宿主可控范围内。
 */
class UserApiHttpClient(
    private val engine: UserApiEngine,
    private val scope: CoroutineScope,
) {

    fun handle(request: UserApiAction.Request) {
        scope.launch(Dispatchers.IO) {
            runCatching { execute(request) }
                .onSuccess { result ->
                    engine.sendHttpResponse(
                        requestKey = request.requestKey,
                        statusCode = result.statusCode,
                        statusMessage = result.statusMessage,
                        headers = result.headers,
                        body = result.body,
                    )
                }
                .onFailure { error ->
                    engine.sendHttpResponse(
                        requestKey = request.requestKey,
                        statusCode = 0,
                        statusMessage = "",
                        headers = emptyMap(),
                        body = null,
                        error = error.message ?: "request failed",
                    )
                }
        }
    }

    private suspend fun execute(request: UserApiAction.Request): HttpResult = withContext(Dispatchers.IO) {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method.uppercase()
            connectTimeout = request.timeoutMs.toInt()
            readTimeout = request.timeoutMs.toInt()
            instanceFollowRedirects = true
            request.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (!request.body.isNullOrEmpty()) {
                doOutput = true
                outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText)
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { it.value.joinToString("; ") }
            HttpResult(code, connection.responseMessage.orEmpty(), headers, body)
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResult(
        val statusCode: Int,
        val statusMessage: String,
        val headers: Map<String, String>,
        val body: String?,
    )
}