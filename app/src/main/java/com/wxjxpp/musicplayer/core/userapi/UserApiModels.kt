package com.wxjxpp.musicplayer.core.userapi

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
)

/** 引擎状态，UI 据此显示"初始化中/可用/失败"。 */
sealed interface UserApiStatus {
    data object Idle : UserApiStatus
    data object Initializing : UserApiStatus
    data class Ready(val info: UserApiInfo) : UserApiStatus
    data class Failed(val message: String) : UserApiStatus
}

/**
 * 脚本发给宿主的动作。
 *
 * 与 preload 脚本里的 `__lx_native_call__(key, action, data)` 一一对应。
 */
sealed interface UserApiAction {
    data class Init(val status: Boolean, val errorMessage: String?) : UserApiAction

    /** 脚本请求宿主代发 HTTP。宿主拿到响应后要回调 `response`。 */
    data class Request(
        val requestKey: String,
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val body: String?,
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