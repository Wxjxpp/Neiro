package com.wxjxpp.musicplayer.core.userapi

import android.content.Context
import com.wxjxpp.musicplayer.core.net.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI

/**
 * 自定义音源脚本存储。
 *
 * 脚本正文按 id 存成独立文件，元信息集中存一个 JSON，
 * 避免大脚本挤在 SharedPreferences / DataStore 里。
 *
 * 导入前一律先过 [validate]：地址打不开、返回的不是 JS、脚本缺少 LX 协议标记，
 * 都在这里被拦下并给出可读原因，而不是静默失败。
 */
class UserApiStore(
    context: Context,
    private val http: HttpClient = HttpClient(),
) {

    /** 导入失败时抛出，消息直接可以展示给用户。 */
    class ImportException(message: String) : Exception(message)

    private val root = File(context.filesDir, "user_api").apply { mkdirs() }
    private val indexFile = File(root, "index.json")

    private val _apis = MutableStateFlow<List<UserApiInfo>>(emptyList())
    val apis: StateFlow<List<UserApiInfo>> = _apis.asStateFlow()

    init {
        _apis.value = readIndex()
    }

    fun scriptFile(id: String): File = File(root, "$id.js")

    suspend fun readScript(id: String): String? = withContext(Dispatchers.IO) {
        scriptFile(id).takeIf { it.isFile }?.readText()
    }

    /**
     * 静态校验脚本内容。
     *
     * 只做"能明确判死"的检查：
     * - 空内容 / 体积异常
     * - 是 HTML（十有八九是把网页地址当成了脚本地址）
     * - 没有任何 LX 协议特征（`lx.on` / `lx.send` / `EVENT_NAMES`）
     *
     * 真正的运行期错误由 [UserApiEngine] 初始化时上报。
     */
    fun validate(script: String, sourceUrl: String? = null): UserApiValidation {
        val trimmed = script.trim()
        if (trimmed.isEmpty()) return UserApiValidation.Invalid("脚本内容为空")
        if (trimmed.length > MAX_SCRIPT_BYTES) {
            return UserApiValidation.Invalid("脚本过大（超过 ${MAX_SCRIPT_BYTES / 1024} KB），已拒绝导入")
        }
        val head = trimmed.take(512).lowercase()
        if (head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<head>")) {
            return UserApiValidation.Invalid("返回的是网页而不是 JS 脚本，请检查地址是否为 .js 直链")
        }
        if (head.startsWith("{") || head.startsWith("[")) {
            return UserApiValidation.Invalid("返回的是 JSON 而不是 JS 脚本，请检查地址")
        }
        val hasLxProtocol = LX_MARKERS.any { trimmed.contains(it) }
        if (!hasLxProtocol) {
            return UserApiValidation.Invalid(
                "不是 LX 格式的音源脚本：未找到 lx.on(...) / lx.send(...) 调用"
            )
        }

        val meta = parseHeader(trimmed)
        val name = meta["name"]?.takeIf { it.isNotBlank() }
        if (name == null) {
            return UserApiValidation.Invalid("脚本缺少 `@name` 头部声明，无法确定音源名称")
        }
        val id = meta["id"]?.takeIf { it.isNotBlank() }
            ?: "api_${name.stableHash()}"
        return UserApiValidation.Valid(
            UserApiInfo(
                id = id,
                name = name,
                description = meta["description"].orEmpty(),
                version = meta["version"].orEmpty(),
                author = meta["author"].orEmpty(),
                homepage = meta["homepage"].orEmpty(),
                sourceUrl = sourceUrl,
                importedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * 导入脚本。
     *
     * 校验不通过时抛 [ImportException]，消息可直接提示用户。
     */
    suspend fun import(script: String, sourceUrl: String? = null): UserApiInfo = withContext(Dispatchers.IO) {
        when (val validation = validate(script, sourceUrl)) {
            is UserApiValidation.Invalid -> throw ImportException(validation.reason)
            is UserApiValidation.Valid -> {
                val info = validation.info
                scriptFile(info.id).writeText(script)
                // 同 id 视为更新：保留原有能力表，等脚本初始化后再刷新
                val previous = _apis.value.firstOrNull { it.id == info.id }
                val merged = info.copy(
                    supportedActions = previous?.supportedActions.orEmpty(),
                    supportedQualities = previous?.supportedQualities.orEmpty(),
                )
                val list = _apis.value.filterNot { it.id == info.id } + merged
                writeIndex(list)
                _apis.value = list
                merged
            }
        }
    }

    /**
     * 从 URL 下载脚本再导入。
     *
     * 逐层给出失败原因：地址格式 → 网络 → 状态码 → 内容类型 → 脚本协议。
     * 脚本可代表用户联网，因此只应从可信来源导入。
     */
    suspend fun importFromUrl(url: String): UserApiInfo = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        if (normalized.isEmpty()) throw ImportException("请输入脚本地址")
        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: throw ImportException("地址格式不正确：$normalized")
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw ImportException("只支持 http / https 地址，当前为：${scheme ?: "空"}")
        }
        if (uri.host.isNullOrBlank()) throw ImportException("地址缺少主机名：$normalized")

        val response = runCatching { http.get(normalized) }
            .getOrElse { error ->
                throw ImportException("下载失败：${error.friendlyMessage()}")
            }
        if (!response.isSuccessful) {
            throw ImportException("下载失败：服务器返回 HTTP ${response.statusCode}")
        }
        val contentType = response.headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.lowercase()
            .orEmpty()
        if (contentType.contains("text/html")) {
            throw ImportException("该地址返回的是网页（Content-Type: text/html），请使用 .js 直链")
        }
        import(response.body, sourceUrl = normalized)
    }

    /** 按记录的来源地址重新拉取脚本。没有来源地址时抛异常。 */
    suspend fun update(id: String): UserApiInfo = withContext(Dispatchers.IO) {
        val info = _apis.value.firstOrNull { it.id == id }
            ?: throw ImportException("音源不存在")
        val url = info.sourceUrl
            ?: throw ImportException("「${info.name}」是本地导入的脚本，没有可更新的地址")
        importFromUrl(url)
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        scriptFile(id).delete()
        val list = _apis.value.filterNot { it.id == id }
        writeIndex(list)
        _apis.value = list
    }

    /** 脚本初始化成功后回写它上报的能力表。 */
    fun updateCapabilities(
        id: String,
        actions: Map<String, List<String>>,
        qualities: Map<String, List<String>>,
    ) {
        val list = _apis.value.map { info ->
            if (info.id == id) {
                info.copy(supportedActions = actions, supportedQualities = qualities)
            } else {
                info
            }
        }
        if (list == _apis.value) return
        writeIndex(list)
        _apis.value = list
    }

    /** 解析脚本头部的 `// @key value` 注释块。 */
    private fun parseHeader(script: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val regex = Regex("""^\s*(?://|\*|/\*)?\s*@(\w+)\s+(.+)$""")
        for (line in script.lineSequence().take(80)) {
            val m = regex.find(line) ?: continue
            val key = m.groupValues[1].lowercase()
            if (result.containsKey(key)) continue
            result[key] = m.groupValues[2].trim().removeSuffix("*/").trim()
        }
        return result
    }

    /** 没有 `@id` 时用名称生成稳定 id，避免每次导入都变成新条目。 */
    private fun String.stableHash(): String =
        (hashCode().toLong() and 0xFFFFFFFFL).toString(16)

    private fun Throwable.friendlyMessage(): String = when (this) {
        is java.net.UnknownHostException -> "无法解析域名（检查网络或地址拼写）"
        is java.net.SocketTimeoutException -> "连接超时"
        is javax.net.ssl.SSLException -> "HTTPS 证书校验失败"
        else -> message ?: this::class.java.simpleName
    }

    private fun readIndex(): List<UserApiInfo> = runCatching {
        if (!indexFile.isFile) return emptyList()
        val array = JSONArray(indexFile.readText())
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            UserApiInfo(
                id = id,
                name = o.optString("name").ifBlank { id },
                description = o.optString("description"),
                version = o.optString("version"),
                author = o.optString("author"),
                homepage = o.optString("homepage"),
                allowShowUpdateAlert = o.optBoolean("allowShowUpdateAlert", true),
                sourceUrl = o.optString("sourceUrl").takeIf { it.isNotBlank() },
                importedAt = o.optLong("importedAt"),
                supportedActions = o.optJSONObject("supportedActions").toStringListMap(),
                supportedQualities = o.optJSONObject("supportedQualities").toStringListMap(),
            )
        }
    }.getOrElse { emptyList() }

    private fun writeIndex(list: List<UserApiInfo>) {
        val array = JSONArray()
        list.forEach { info ->
            array.put(
                JSONObject().apply {
                    put("id", info.id)
                    put("name", info.name)
                    put("description", info.description)
                    put("version", info.version)
                    put("author", info.author)
                    put("homepage", info.homepage)
                    put("allowShowUpdateAlert", info.allowShowUpdateAlert)
                    info.sourceUrl?.let { put("sourceUrl", it) }
                    put("importedAt", info.importedAt)
                    put("supportedActions", info.supportedActions.toJson())
                    put("supportedQualities", info.supportedQualities.toJson())
                }
            )
        }
        runCatching { indexFile.writeText(array.toString()) }
    }

    private fun JSONObject?.toStringListMap(): Map<String, List<String>> {
        if (this == null) return emptyMap()
        return buildMap {
            keys().forEach { key ->
                val array = optJSONArray(key) ?: return@forEach
                put(key, (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } })
            }
        }
    }

    private fun Map<String, List<String>>.toJson(): JSONObject = JSONObject().also { json ->
        forEach { (key, values) -> json.put(key, JSONArray(values)) }
    }

    private companion object {
        /** 单个脚本上限 2 MB，正常 LX 脚本远小于此。 */
        const val MAX_SCRIPT_BYTES = 2 * 1024 * 1024

        /** LX 协议特征：脚本必须注册 request 处理器并上报初始化。 */
        val LX_MARKERS = listOf("lx.on(", "lx.send(", "EVENT_NAMES", "globalThis.lx")
    }
}

