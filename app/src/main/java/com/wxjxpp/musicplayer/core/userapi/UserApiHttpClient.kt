package com.wxjxpp.musicplayer.core.userapi

import android.util.Base64
import com.wxjxpp.musicplayer.core.net.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 代脚本发起 HTTP 请求。
 *
 * 脚本自身不能直接联网：它通过 `request` 动作把 URL / 方法 / 头交给宿主，
 * 宿主发完再用 [UserApiEngine.sendHttpResponse] 回填。
 * 这样所有网络行为都在宿主可控范围内。
 *
 * 三个必须照顾到的细节：
 * - `form` 需要转成 `application/x-www-form-urlencoded` 编码体
 * - `binary: true` 时脚本期待的是字节数组（这里给 base64 之外还给出数字数组，
 *   与 preload 的 `utils.buffer.from(x, 'base64')` 用法对齐）
 * - 脚本可能 `cancelRequest`，因此每个请求要能被取消
 */
class UserApiHttpClient(
    private val engine: UserApiEngine,
    private val scope: CoroutineScope,
    private val http: HttpClient = HttpClient(),
) {

    /** requestKey → 正在执行的任务，供取消使用。 */
    private val inflight = ConcurrentHashMap<String, Job>()

    fun handle(request: UserApiAction.Request) {
        val job = scope.launch(Dispatchers.IO) {
            runCatching { execute(request) }
                .onSuccess { result ->
                    engine.sendHttpResponse(
                        requestKey = request.requestKey,
                        statusCode = result.statusCode,
                        statusMessage = if (result.statusCode in 200..299) "OK" else "Error",
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
                        error = error.friendlyMessage(),
                    )
                }
            inflight.remove(request.requestKey)
        }
        inflight[request.requestKey] = job
    }

    /** 脚本主动取消。 */
    fun cancel(requestKey: String) {
        inflight.remove(requestKey)?.cancel()
    }

    private suspend fun execute(request: UserApiAction.Request): HttpClient.Response {
        val method = request.method.uppercase()
        val headers = request.headers.toMutableMap()
        var body: ByteArray? = null

        // form 优先：脚本用 form 时不会同时给 body
        val form = request.form?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (form != null) {
            headers.putIfAbsent("Content-Type", "application/x-www-form-urlencoded")
            body = form.toFormBody().toByteArray(Charsets.UTF_8)
        } else if (!request.body.isNullOrBlank()) {
            headers.putIfAbsent("Content-Type", "application/json")
            body = request.body.toByteArray(Charsets.UTF_8)
        }

        val response = http.execute(
            url = request.url,
            method = method,
            body = body,
            headers = headers,
        )

        // 二进制响应转 base64，脚本侧用 utils.buffer.from(x, 'base64') 还原
        if (request.binary) {
            return response.copy(
                body = Base64.encodeToString(response.bytes, Base64.NO_WRAP)
            )
        }
        return response
    }

    /** `{a: 1, b: "x y"}` → `a=1&b=x%20y`。 */
    private fun JSONObject.toFormBody(): String = buildString {
        keys().forEach { key ->
            if (isNotEmpty()) append('&')
            append(java.net.URLEncoder.encode(key, "UTF-8"))
            append('=')
            append(java.net.URLEncoder.encode(optString(key), "UTF-8"))
        }
    }

    private fun Throwable.friendlyMessage(): String = when (this) {
        is java.net.UnknownHostException -> "无法解析域名：$message"
        is java.net.SocketTimeoutException -> "请求超时"
        is javax.net.ssl.SSLException -> "TLS 握手失败：$message"
        else -> message ?: "request failed"
    }
}