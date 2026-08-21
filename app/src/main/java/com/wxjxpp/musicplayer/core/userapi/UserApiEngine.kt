package com.wxjxpp.musicplayer.core.userapi

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import com.wxjxpp.musicplayer.core.crypto.AES
import com.wxjxpp.musicplayer.core.crypto.RSA
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * QuickJS 自定义音源引擎。
 *
 * 沿用 LX-Pro-Music 的脚本协议，因此 `assets/script/user-api-preload.js`
 * 与现有用户脚本可直接复用：
 *
 * 1. 宿主注入 `__lx_native_call__` 及一组 `__lx_native_call__utils_*` 工具函数
 * 2. 宿主 `evaluate(preload)` 后调用 `lx_setup(key, id, name, ...)`
 * 3. 宿主 `evaluate(userScript)` 加载用户脚本
 * 4. 双向通信：脚本 → `__lx_native_call__(key, action, data)`；
 *    宿主 → `__lx_native__(key, action, ...args)`
 *
 * JS 上下文不是线程安全的，因此所有 evaluate/call 都固定在同一 HandlerThread 上执行。
 */
class UserApiEngine(private val context: Context) {

    private val thread = HandlerThread("user-api-js").apply { start() }
    private val handler = Handler(thread.looper)

    /** 宿主与脚本约定的调用密钥，防止脚本内部伪造调用。 */
    private var key: String = UUID.randomUUID().toString()
    private var jsContext: QuickJSContext? = null
    private var loaderInited = false
    private var initialized = false

    private val _status = MutableStateFlow<UserApiStatus>(UserApiStatus.Idle)
    val status: StateFlow<UserApiStatus> = _status.asStateFlow()

    private val _actions = MutableSharedFlow<UserApiAction>(extraBufferCapacity = 64)
    val actions: SharedFlow<UserApiAction> = _actions.asSharedFlow()

    /** 由脚本发起、等待宿主回填的 HTTP 请求。 */
    private val pendingRequests = ConcurrentHashMap<String, UserApiAction.Request>()

    /** 加载并初始化一个音源脚本。 */
    fun loadScript(info: UserApiInfo, script: String) {
        _status.value = UserApiStatus.Initializing
        handler.post {
            runCatching {
                initialized = false
                createContext(info, script)
            }.onFailure { error ->
                val message = error.message ?: "脚本加载失败"
                Log.e(TAG, "loadScript failed: $message")
                _status.value = UserApiStatus.Failed(message)
                _actions.tryEmit(UserApiAction.Init(status = false, errorMessage = message))
            }
        }
    }

    /** 把宿主代发的 HTTP 结果回给脚本。 */
    fun sendHttpResponse(
        requestKey: String,
        statusCode: Int,
        statusMessage: String,
        headers: Map<String, String>,
        body: String?,
        error: String? = null,
    ) {
        pendingRequests.remove(requestKey)
        val payload = JSONObject().apply {
            put("requestKey", requestKey)
            put("error", error)
            if (error == null) {
                put(
                    "response",
                    JSONObject().apply {
                        put("statusCode", statusCode)
                        put("statusMessage", statusMessage)
                        put("headers", JSONObject(headers as Map<*, *>))
                        put("body", body)
                    }
                )
            } else {
                put("response", JSONObject.NULL)
            }
        }
        callJs("response", payload.toString())
    }

    /** 调用脚本导出的能力，例如取播放地址。data 为 JSON 字符串。 */
    fun callAction(action: String, dataJson: String) = callJs(action, dataJson)

    fun destroy() {
        handler.post {
            runCatching { jsContext?.close() }
            jsContext = null
            initialized = false
            _status.value = UserApiStatus.Idle
        }
    }

    // ---- 内部实现 ----

    private fun createContext(info: UserApiInfo, script: String) {
        if (!loaderInited) {
            QuickJSLoader.init()
            loaderInited = true
        }
        runCatching { jsContext?.close() }
        key = UUID.randomUUID().toString()

        val ctx = QuickJSContext.create()
        jsContext = ctx
        injectNativeBridge(ctx)

        val preload = readAsset("script/user-api-preload.js")
            ?: error("缺少 user-api-preload.js")
        ctx.evaluate(preload)

        // preload 暴露 lx_setup(key, id, name, description, version, author, homepage, rawScript)
        ctx.globalObject.getJSFunction("lx_setup").call(
            key,
            info.id,
            info.name,
            info.description,
            info.version,
            info.author,
            info.homepage,
            script,
        )

        // 再执行用户脚本本体
        runCatching { ctx.evaluate(script) }.onFailure { error ->
            val message = error.message ?: "脚本执行失败"
            runCatching { callJsInternal("__run_error__") }
            if (!initialized) {
                initialized = true
                _status.value = UserApiStatus.Failed(message)
                _actions.tryEmit(UserApiAction.Init(status = false, errorMessage = message))
            }
            return
        }
    }

    /**
     * 注入脚本可用的宿主能力。
     *
     * 名称与 preload 脚本约定一致，改名会导致现有用户脚本失效。
     */
    private fun injectNativeBridge(ctx: QuickJSContext) {
        val global = ctx.globalObject

        global.setProperty("__lx_native_call__", JSCallFunction { args ->
            if (args.size >= 3 && key == args[0]) {
                onScriptAction(args[1] as String, args[2] as? String ?: "")
            }
            null
        })

        global.setProperty("__lx_native_call__utils_str2b64", JSCallFunction { args ->
            runCatching {
                Base64.encodeToString((args[0] as String).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }.getOrDefault("")
        })

        global.setProperty("__lx_native_call__utils_b642buf", JSCallFunction { args ->
            runCatching {
                val bytes = Base64.decode(args[0] as String, Base64.NO_WRAP)
                bytes.joinToString(prefix = "[", postfix = "]") { it.toInt().toString() }
            }.getOrDefault("")
        })

        global.setProperty("__lx_native_call__utils_str2md5", JSCallFunction { args ->
            runCatching {
                val decoded = URLDecoder.decode(args[0] as String, "UTF-8")
                MessageDigest.getInstance("MD5")
                    .digest(decoded.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
            }.getOrDefault("")
        })

        global.setProperty("__lx_native_call__utils_aes_encrypt", JSCallFunction { args ->
            runCatching {
                AES.encrypt(args[0] as String, args[1] as String, args[2] as String, args[3] as String)
            }.getOrDefault("")
        })

        global.setProperty("__lx_native_call__utils_rsa_encrypt", JSCallFunction { args ->
            runCatching {
                RSA.encryptRSAToString(args[0] as String, args[1] as String, args[2] as String)
            }.getOrDefault("")
        })

        global.setProperty("__lx_native_call__set_timeout", JSCallFunction { args ->
            val callbackId = args[0]
            val delay = (args[1] as? Number)?.toLong() ?: 0L
            handler.postDelayed({ callJsInternal("__set_timeout__", callbackId) }, delay)
            null
        })
    }

    /** 脚本 → 宿主。 */
    private fun onScriptAction(action: String, data: String) {
        Log.d(TAG, "script action=$action")
        when (action) {
            "init" -> {
                if (initialized) return
                initialized = true
                val json = data.toJsonOrNull()
                val ok = json?.optBoolean("status", true) ?: true
                val error = json?.optString("errorMessage")?.takeIf { it.isNotBlank() }
                _status.value = if (ok) {
                    UserApiStatus.Ready(currentInfo(json))
                } else {
                    UserApiStatus.Failed(error ?: "初始化失败")
                }
                _actions.tryEmit(UserApiAction.Init(ok, error))
            }

            "request" -> data.toJsonOrNull()?.let { json ->
                val options = json.optJSONObject("options")
                val headersJson = options?.optJSONObject("headers")
                val headers = buildMap {
                    headersJson?.keys()?.forEach { k -> put(k, headersJson.optString(k)) }
                }
                val request = UserApiAction.Request(
                    requestKey = json.optString("requestKey"),
                    url = json.optString("url"),
                    method = options?.optString("method")?.ifBlank { "GET" } ?: "GET",
                    headers = headers,
                    body = options?.opt("data")?.takeIf { it != JSONObject.NULL }?.toString(),
                    timeoutMs = options?.optLong("timeout", 15_000L) ?: 15_000L,
                    binary = options?.optBoolean("binary", false) ?: false,
                )
                pendingRequests[request.requestKey] = request
                _actions.tryEmit(request)
            }

            "cancelRequest" -> {
                pendingRequests.remove(data)
                _actions.tryEmit(UserApiAction.CancelRequest(data))
            }

            "response" -> data.toJsonOrNull()?.let { json ->
                _actions.tryEmit(
                    UserApiAction.Response(
                        requestKey = json.optString("requestKey"),
                        status = json.optBoolean("status", false),
                        errorMessage = json.optString("errorMessage").takeIf { it.isNotBlank() },
                        resultJson = json.opt("result")?.takeIf { it != JSONObject.NULL }?.toString(),
                    )
                )
            }

            "showUpdateAlert" -> data.toJsonOrNull()?.let { json ->
                _actions.tryEmit(
                    UserApiAction.ShowUpdateAlert(
                        name = json.optString("name"),
                        log = json.optString("log"),
                        updateUrl = json.optString("updateUrl"),
                    )
                )
            }

            "log" -> _actions.tryEmit(UserApiAction.Log("info", data))
        }
    }

    /** 宿主 → 脚本，统一切到 JS 线程。 */
    private fun callJs(action: String, vararg args: Any?) {
        handler.post { callJsInternal(action, *args) }
    }

    private fun callJsInternal(action: String, vararg args: Any?): Any? {
        val ctx = jsContext ?: return null
        return runCatching {
            val params = arrayOf<Any?>(key, action, *args)
            ctx.globalObject.getJSFunction("__lx_native__").call(*params)
        }.onFailure { error ->
            val message = error.message?.take(1024) ?: "调用脚本失败"
            Log.e(TAG, "callJs($action) failed: $message")
            _actions.tryEmit(UserApiAction.Log("error", message))
            if (!initialized) {
                initialized = true
                _status.value = UserApiStatus.Failed(message)
            }
        }.getOrNull()
    }

    private fun currentInfo(json: JSONObject?): UserApiInfo {
        val infoJson = json?.optJSONObject("info")
        return UserApiInfo(
            id = infoJson?.optString("id").orEmpty(),
            name = infoJson?.optString("name").orEmpty().ifBlank { "自定义音源" },
            description = infoJson?.optString("description").orEmpty(),
            version = infoJson?.optString("version").orEmpty(),
            author = infoJson?.optString("author").orEmpty(),
            homepage = infoJson?.optString("homepage").orEmpty(),
        )
    }

    private fun readAsset(path: String): String? = runCatching {
        context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    private fun String.toJsonOrNull(): JSONObject? =
        runCatching { JSONObject(this) }.getOrNull()

    private companion object {
        const val TAG = "UserApiEngine"
    }
}