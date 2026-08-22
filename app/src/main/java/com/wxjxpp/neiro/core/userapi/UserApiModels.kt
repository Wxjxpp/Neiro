package com.wxjxpp.neiro.core.userapi

/**
 * 自定义音源脚本元信息。
 *
 * 字段与 LX-Pro-Music 的 UserApiInfo 对齐，
 * 因此现有的用户脚本（`user-api-preload.js` 协议）可以直接沿用。
 */
data class UserApiInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val homepage: String = "",
    val allowShowUpdateAlert: Boolean = true,
    /** 脚本来源：本地文件为 null，URL 导入时记下地址便于"检查更新"。 */
    val sourceUrl: String? = null,
    /** 导入时间，用于列表排序。 */
    val importedAt: Long = 0L,
    /**
     * 脚本初始化后上报的能力表：`平台 → 支持的动作`。
     *
     * 例如 `{"kw": ["musicUrl"], "wy": ["musicUrl", "lyric"]}`。
     * UI 用它显示"该音源支持哪些平台"，播放层用它决定能否解析地址。
     */
    val supportedActions: Map<String, List<String>> = emptyMap(),
    /** 能力表：`平台 → 支持的音质`。 */
    val supportedQualities: Map<String, List<String>> = emptyMap(),
) {
    /** 该脚本支持的平台列表（有任意动作即算支持）。 */
    val platforms: List<String> get() = supportedActions.keys.toList()
}

/** 引擎状态，UI 据此显示"初始化中 / 可用 / 失败"。 */
sealed interface UserApiStatus {
    data object Idle : UserApiStatus
    data class Initializing(val id: String) : UserApiStatus
    data class Ready(val info: UserApiInfo) : UserApiStatus
    /** [id] 为空表示还没确定是哪个脚本就失败了（例如脚本语法错误）。 */
    data class Failed(val id: String, val message: String) : UserApiStatus
}

/**
 * 脚本发给宿主的动作。
 *
 * 与 preload 脚本里的 `__lx_native_call__(key, action, data)` 一一对应。
 */
sealed interface UserApiAction {
    data class Init(
        val status: Boolean,
        val errorMessage: String?,
        val supportedActions: Map<String, List<String>> = emptyMap(),
        val supportedQualities: Map<String, List<String>> = emptyMap(),
    ) : UserApiAction

    /** 脚本请求宿主代发 HTTP。宿主拿到响应后要回调 `response`。 */
    data class Request(
        val requestKey: String,
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val body: String?,
        val form: String?,
        val timeoutMs: Long,
        val binary: Boolean,
    ) : UserApiAction

    data class CancelRequest(val requestKey: String) : UserApiAction

    /** 脚本对某次调用（如取播放地址）的返回。 */
    data class Response(
        val requestKey: String,
        val status: Boolean,
        val errorMessage: String?,
        val resultJson: String?,
    ) : UserApiAction

    data class ShowUpdateAlert(val name: String, val log: String, val updateUrl: String) : UserApiAction

    data class Log(val level: String, val message: String) : UserApiAction
}

/**
 * 脚本校验结果。
 *
 * 导入前先做静态检查，把"这根本不是音源脚本"和"脚本跑不起来"区分开，
 * 前者能给出明确原因，不必等 QuickJS 初始化超时。
 */
sealed interface UserApiValidation {
    data class Valid(val info: UserApiInfo) : UserApiValidation
    data class Invalid(val reason: String) : UserApiValidation
}