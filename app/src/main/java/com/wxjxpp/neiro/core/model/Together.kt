package com.wxjxpp.neiro.core.model

/**
 * 一起听（Listen Together）模型。
 *
 * 传输层（内网穿透 / WebSocket / 自建后端）在 core/together 里抽象，
 * 这里只定义房间与同步指令，换传输方式不影响业务。
 */

data class TogetherMember(
    val id: String,
    val name: String,
    val isHost: Boolean = false,
)

data class TogetherRoom(
    val id: String,
    val name: String,
    val members: List<TogetherMember> = emptyList(),
    val hostId: String? = null,
)

/** 房间内广播的同步事件。 */
sealed interface TogetherEvent {
    data class Play(val songId: String, val positionMs: Long, val atServerTimeMs: Long) : TogetherEvent
    data class Pause(val positionMs: Long, val atServerTimeMs: Long) : TogetherEvent
    data class Seek(val positionMs: Long, val atServerTimeMs: Long) : TogetherEvent
    data class QueueChanged(val songIds: List<String>) : TogetherEvent
    data class MemberChanged(val members: List<TogetherMember>) : TogetherEvent
    data class Chat(val fromId: String, val text: String, val atMs: Long) : TogetherEvent
}

enum class TogetherConnectionState { Disconnected, Connecting, Connected, Reconnecting, Failed }