package com.wxjxpp.musicplayer.core.userapi

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 自定义音源脚本存储。
 *
 * 脚本正文按 id 存成独立文件，元信息集中存一个 JSON，
 * 避免大脚本挤在 SharedPreferences / DataStore 里。
 */
class UserApiStore(context: Context) {

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
     * 导入脚本。
     *
     * 元信息从脚本头部注释解析（与 LX 用户脚本一致的 `@name` / `@version` 等标记）。
     */
    suspend fun import(script: String): UserApiInfo = withContext(Dispatchers.IO) {
        val meta = parseHeader(script)
        val id = meta["id"]?.takeIf { it.isNotBlank() }
            ?: "api_${System.currentTimeMillis()}"
        val info = UserApiInfo(
            id = id,
            name = meta["name"] ?: "未命名音源",
            description = meta["description"].orEmpty(),
            version = meta["version"].orEmpty(),
            author = meta["author"].orEmpty(),
            homepage = meta["homepage"].orEmpty(),
        )
        scriptFile(id).writeText(script)
        val list = _apis.value.filterNot { it.id == id } + info
        writeIndex(list)
        _apis.value = list
        info
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        scriptFile(id).delete()
        val list = _apis.value.filterNot { it.id == id }
        writeIndex(list)
        _apis.value = list
    }

    /** 解析脚本头部的 `// @key value` 注释块。 */
    private fun parseHeader(script: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val regex = Regex("""^\s*(?://|\*)?\s*@(\w+)\s+(.+)$""")
        for (line in script.lineSequence().take(60)) {
            val m = regex.find(line) ?: continue
            result[m.groupValues[1].lowercase()] = m.groupValues[2].trim()
        }
        return result
    }

    private fun readIndex(): List<UserApiInfo> = runCatching {
        if (!indexFile.isFile) return emptyList()
        val array = JSONArray(indexFile.readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            UserApiInfo(
                id = o.getString("id"),
                name = o.optString("name"),
                description = o.optString("description"),
                version = o.optString("version"),
                author = o.optString("author"),
                homepage = o.optString("homepage"),
                allowShowUpdateAlert = o.optBoolean("allowShowUpdateAlert", true),
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
                }
            )
        }
        indexFile.writeText(array.toString())
    }
}