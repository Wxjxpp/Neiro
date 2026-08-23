package com.wxjxpp.neiro.core.userapi

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.util.UUID

/**
 * 自定义音源脚本调用客户端。
 *
 * 把 [UserApiEngine] 的"发出请求 → 等待同 requestKey 的 response"异步协议
 * 包成挂起函数，调用方只管 `await` 结果。
 *
 * 之所以单独一层：LX 协议的 request/response 是全局事件流，
 * 并发调用时必须靠 requestKey 对号入座，这段逻辑不该散落在各个音源里。
 */
class UserApiClient(
    private val engine: UserApiEngine,
    // 聚合脚本（如墨澜/星海）会串行尝试多个后端，每个 8~10s，
    // 总耗时轻松超过 20s，这里放宽到 60s 避免误判超时。
    private val timeoutMs: Long = 60_000L,
) {

    /** 调用结果。失败时带上可展示的原因。 */
    sealed interface Result {
        data class Success(val dataJson: String?) : Result
        data class Failure(val reason: String) : Result
    }

    /**
     * 取播放地址。
     *
     * @param source   平台标识（kw / kg / tx / wy / mg）
     * @param quality  脚本音质字符串（128k / 320k / flac / hires ...）
     * @param musicInfo 平台搜索返回的原始 JSON，脚本需要里面的平台特有字段
     */
    suspend fun musicUrl(source: String, quality: String, musicInfo: JSONObject): Result {
        if (!engine.isReady) return Result.Failure("没有可用的自定义音源，请先在「自定义音源」里导入并启用脚本")
        val info = JSONObject().apply {
            put("type", quality)
            put("musicInfo", musicInfo)
        }
        return when (val result = call(source, "musicUrl", info)) {
            is Result.Failure -> result
            is Result.Success -> {
                // 脚本返回 {source, action, data: {type, url}}
                val url = result.dataJson
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                    ?.optJSONObject("data")
                    ?.optString("url")
                    ?.takeIf { it.startsWith("http") }
                if (url == null) Result.Failure("音源脚本没有返回可用的播放地址") else Result.Success(url)
            }
        }
    }

    /** 取歌词。返回脚本给的 `{lyric, tlyric, rlyric, lxlyric}` JSON。 */
    suspend fun lyric(source: String, musicInfo: JSONObject): Result {
        if (!engine.isReady) return Result.Failure("音源未就绪")
        val info = JSONObject().apply { put("musicInfo", musicInfo) }
        return when (val result = call(source, "lyric", info)) {
            is Result.Failure -> result
            is Result.Success -> {
                val data = result.dataJson
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                    ?.optJSONObject("data")
                if (data == null) Result.Failure("音源脚本没有返回歌词") else Result.Success(data.toString())
            }
        }
    }

    /** 取封面地址。 */
    suspend fun pic(source: String, musicInfo: JSONObject): Result {
        if (!engine.isReady) return Result.Failure("音源未就绪")
        val info = JSONObject().apply { put("musicInfo", musicInfo) }
        return when (val result = call(source, "pic", info)) {
            is Result.Failure -> result
            is Result.Success -> {
                val url = result.dataJson
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                    ?.optString("data")
                    ?.takeIf { it.startsWith("http") }
                if (url == null) Result.Failure("音源脚本没有返回封面") else Result.Success(url)
            }
        }
    }

    /**
     * 发起一次脚本调用并等待其 response。
     *
     * 必须先真正订阅事件流再发请求：`actions` 是 SharedFlow，
     * 若先发请求，脚本可能在订阅建立前就把 response 发了出去，导致永久超时。
     * 这里用 async 抢先启动收集协程，再发请求。
     */
    private suspend fun call(source: String, action: String, info: JSONObject): Result =
        coroutineScope {
            val requestKey = "request__${UUID.randomUUID()}"
            val awaiting = async {
                engine.actions
                    .filterIsInstance<UserApiAction.Response>()
                    .first { it.requestKey == requestKey }
            }
            // 让收集协程先跑到挂起点（订阅完成）
            yield()
            engine.requestAction(requestKey, source, action, info)

            val response = withTimeoutOrNull(timeoutMs) { awaiting.await() }
            if (response == null) {
                awaiting.cancel()
                return@coroutineScope Result.Failure("音源脚本响应超时（${timeoutMs / 1000} 秒）")
            }
            if (response.status) {
                Result.Success(response.resultJson)
            } else {
                Result.Failure(response.errorMessage ?: "音源脚本执行失败")
            }
        }
}