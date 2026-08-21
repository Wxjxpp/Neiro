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
import org.json.JSONArray
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
 *    宿主 → `__lx_native__(key, action, dataJson)`
 *
 * 两个容易踩的点：
 * - JS 上下文不是线程安全的，所有 evaluate/call 都固定在同一 HandlerThread
 * - 脚本可能永远不调用 `lx.send('inited')`，因此必须有初始化超时，
 *   否则 UI 会一直卡在"初始化中"
 */
class UserApiEngine(private val context: Context) {

    private val thread = HandlerThread("user-api-js").apply { start() }
    private val handler = Handler(thread.looper)

    /** 宿主与脚本约定的调用密钥，防止脚本内部伪造调用。 */
    private var key: String = UUID.randomUUID().toString()
    private var jsContext: QuickJSContext? = null
    private var loaderInited = false

    /** 当前正在加载/已加载的脚本 id 与初始化状态。 */
    @Volatile
    private var currentId: String = ""

    @Volatile
    private var initialized = false

    private val _status = MutableStateFlow<UserApiStatus>(UserApiStatus.Idle)
    val status: StateFlow<UserApiStatus> = _status.asStateFlow()

    private val _actions = MutableSharedFlow<UserApiAction>(extraBufferCapacity = 128)
    val actions: SharedFlow<UserApiAction> = _actions.asSharedFlow()

    /** 由脚本发起、等待宿主回填的 HTTP 请求。 */
    private val pendingRequests = ConcurrentHashMap<String, UserApiAction.Request>()

    /** 初始化超时任务，脚本正常上报后取消。 */
    private var initTimeout: Runnable? = null

    /** 引擎是否可用（脚本已初始化成功）。 */
    val isReady: Boolean get() = _status.value is UserApiStatus.Ready

    /** 加载并初始化一个音源脚本。 */
    fun loadScript(info: UserApiInfo, script: String) {
        _status.value = UserApiStatus.Initializing(info.id)
        handler.post {
            currentId = info.id
            initialized = false
            scheduleInitTimeout(info)
            runCatching { createContext(info, script) }.onFailure { error ->
                failInit(info.id, error.message ?: "脚本加载失败")
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
            put("error", error ?: JSONObject.NULL)
            if (error == null) {
                put(
                    "response",
                    JSONObject().apply {
                        put("statusCode", statusCode)
                        put("statusMessage", statusMessage)
                        put("headers", JSONObject(headers))
                        // 脚本侧多数用 JSON.parse 兜底，这里尽量给出对象形态
                        put("body", body?.toJsonValue() ?: JSONObject.NULL)
                    }
                )
            } else {
                put("response", JSONObject.NULL)
            }
        }
        callJs("response", payload.toString())
    }

    /**
     * 请求脚本执行一个动作。
     *
     * 对应 preload 里的 `handleRequest({requestKey, data})`，
     * data 结构必须是 `{source, action, info}`，否则脚本拿不到参数。
     *
     * @param source 平台标识：kw / kg / tx / wy / mg / local
     * @param action musicUrl / lyric / pic
     * @param info   透传给脚本的参数，musicUrl 需要 `{type, musicInfo}`
     */
    fun requestAction(requestKey: String, source: String, action: String, info: JSONObject) {
        val payload = JSONObject().apply {
            put("requestKey", requestKey)
            put(
                "data",
                JSONObject().apply {
                    put("source", source)
                    put("action", action)
                    put("info", info)
                }
            )
        }
        callJs("request", payload.toString())
    }

    fun destroy() {
        handler.post {
            cancelInitTimeout()
            runCatching { jsContext?.close() }
            jsContext = null
            initialized = false
            currentId = ""
            pendingRequests.clear()
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
            ?: error("缺少 user-api-preload.js（构建产物不完整）")
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
            runCatching { callJsInternal("__run_error__") }
            failInit(info.id, "脚本执行出错：${error.message?.take(300) ?: "未知错误"}")
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
                onScriptAction(args[1] as? String ?: "", args[2] as? String ?: "")
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
        when (action) {
            "init" -> handleInit(data)

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
                    body = options?.opt("body")?.takeIf { it != JSONObject.NULL }?.toString(),
                    form = options?.opt("form")?.takeIf { it != JSONObject.NULL }?.toString(),
                    timeoutMs = options?.optLong("timeout", 15_000L)?.takeIf { it > 0 } ?: 15_000L,
                    binary = options?.optBoolean("binary", false) ?: false,
                )
                if (request.url.isBlank()) return
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

    /**
     * 处理脚本上报的初始化结果。
     *
     * preload 传上来的结构是 `{status, errorMessage, info: {sources: {kw: {...}}}}`，
     * 其中 sources 才是真正的能力表。
     */
    private fun handleInit(data: String) {
        if (initialized) return
        initialized = true
        cancelInitTimeout()

        val json = data.toJsonOrNull()
        val ok = json?.optBoolean("status", false) ?: false
        val error = json?.optString("errorMessage")?.takeIf { it.isNotBlank() }
        if (!ok) {
            failInit(currentId, error ?: "脚本初始化失败（未上报原因）", alreadyMarked = true)
            return
        }

        val sources = json?.optJSONObject("info")?.optJSONObject("sources")
        val actionsMap = mutableMapOf<String, List<String>>()
        val qualityMap = mutableMapOf<String, List<String>>()
        sources?.keys()?.forEach { source ->
            val entry = sources.optJSONObject(source) ?: return@forEach
            actionsMap[source] = entry.optJSONArray("actions").toStringList()
            qualityMap[source] = entry.optJSONArray("qualitys").toStringList()
        }

        if (actionsMap.isEmpty()) {
            failInit(currentId, "脚本没有声明任何可用平台，可能与本应用协议不兼容", alreadyMarked = true)
            return
        }

        _actions.tryEmit(
            UserApiAction.Init(
                status = true,
                errorMessage = null,
                supportedActions = actionsMap,
                supportedQualities = qualityMap,
            )
        )
    }

    /** 脚本迟迟不上报 inited 时给出明确失败，而不是无限等待。 */
    private fun scheduleInitTimeout(info: UserApiInfo) {
        cancelInitTimeout()
        val task = Runnable {
            if (initialized) return@Runnable
            initialized = true
            failInit(info.id, "脚本初始化超时（${INIT_TIMEOUT_MS / 1000} 秒未响应）", alreadyMarked = true)
        }
        initTimeout = task
        handler.postDelayed(task, INIT_TIMEOUT_MS)
    }

    private fun cancelInitTimeout() {
        initTimeout?.let { handler.removeCallbacks(it) }
        initTimeout = null
    }

    private fun failInit(id: String, message: String, alreadyMarked: Boolean = false) {
        if (!alreadyMarked) {
            if (initialized) return
            initialized = true
        }
        cancelInitTimeout()
        Log.e(TAG, "user api [$id] failed: $message")
        _status.value = UserApiStatus.Failed(id, message)
        _actions.tryEmit(UserApiAction.Init(status = false, errorMessage = message))
    }

    /** 由外部（容器层）在拿到完整 info 后置为 Ready，保证状态里带能力表。 */
    fun markReady(info: UserApiInfo) {
        _status.value = UserApiStatus.Ready(info)
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
                failInit(currentId, "调用脚本失败：$message", alreadyMarked = true)
            }
        }.getOrNull()
    }

    private fun readAsset(path: String): String? = runCatching {
        context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    private fun String.toJsonOrNull(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()

    /** 响应体优先按 JSON 交给脚本，失败就按字符串原样给。 */
    private fun String.toJsonValue(): Any = runCatching { JSONObject(this) }.getOrNull()
        ?: runCatching { JSONArray(this) }.getOrNull()
        ?: this

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private companion object {
        const val TAG = "UserApiEngine"

        /** 初始化超时：LX 脚本正常在 1-3 秒内完成。 */
        const val INIT_TIMEOUT_MS = 20_000L
    }
}