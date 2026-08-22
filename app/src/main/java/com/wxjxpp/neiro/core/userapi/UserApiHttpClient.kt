package com.wxjxpp.neiro.core.userapi

import com.wxjxpp.neiro.core.net.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


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
    private val http: HttpClient,
) {

    fun handle(request: UserApiAction.Request) {
        scope.launch(Dispatchers.IO) {
            try {
                val resp = http.execute(
                    url = request.url,
                    method = request.method,
                    body = request.body?.toByteArray(Charsets.UTF_8),
                    headers = request.headers,
                )
                engine.sendHttpResponse(
                    requestKey = request.requestKey,
                    statusCode = resp.statusCode,
                    statusMessage = resp.body.take(500),
                    headers = resp.headers,
                    body = resp.body,
                )
            } catch (error: Throwable) {
                engine.sendHttpResponse(
                    requestKey = request.requestKey,
                    statusCode = 0,
                    statusMessage = "",
                    headers = emptyMap(),
                    body = null,
                    error = error.friendlyMessage(),
                )
            }
        }
    }

    private fun Throwable.friendlyMessage(): String = when (this) {
        is java.net.UnknownHostException -> "无法解析域名（检查网络或地址拼写）"
        is java.net.SocketTimeoutException -> "连接超时"
        is javax.net.ssl.SSLException -> "HTTPS 证书校验失败"
        else -> message ?: this::class.java.simpleName
    }
}