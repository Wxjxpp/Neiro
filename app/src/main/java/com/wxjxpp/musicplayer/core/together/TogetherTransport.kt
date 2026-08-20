package com.wxjxpp.musicplayer.core.together

import com.wxjxpp.musicplayer.core.model.TogetherConnectionState
import com.wxjxpp.musicplayer.core.model.TogetherEvent
import com.wxjxpp.musicplayer.core.model.TogetherRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 一起听传输层契约。
 *
 * 具体实现可以是：
 * - 自建后端 WebSocket
 * - 内网穿透 / P2P
 * - 局域网直连
 *
 * 业务层只依赖这个接口，因此换传输方案不影响播放与 UI。
 * 时钟同步（[serverTimeOffsetMs]）放在这一层，
 * 因为不同传输方式的对时手段不同。
 */
interface TogetherTransport {

    val connectionState: StateFlow<TogetherConnectionState>
    val room: StateFlow<TogetherRoom?>

    /** 服务端时间与本机时间的差值，用于把远端事件换算到本地时钟。 */
    val serverTimeOffsetMs: StateFlow<Long>

    fun events(): Flow<TogetherEvent>

    suspend fun createRoom(name: String): Result<TogetherRoom>
    suspend fun joinRoom(roomId: String): Result<TogetherRoom>
    suspend fun leaveRoom()
    suspend fun send(event: TogetherEvent): Result<Unit>
}

/** 未接入传输时的占位实现，保证 UI 可以先跑起来。 */
class NoopTogetherTransport : TogetherTransport {

    override val connectionState = MutableStateFlow(TogetherConnectionState.Disconnected)
    override val room = MutableStateFlow<TogetherRoom?>(null)
    override val serverTimeOffsetMs = MutableStateFlow(0L)

    override fun events(): Flow<TogetherEvent> = emptyFlow()

    override suspend fun createRoom(name: String): Result<TogetherRoom> =
        Result.failure(UnsupportedOperationException("尚未接入一起听服务"))

    override suspend fun joinRoom(roomId: String): Result<TogetherRoom> =
        Result.failure(UnsupportedOperationException("尚未接入一起听服务"))

    override suspend fun leaveRoom() = Unit

    override suspend fun send(event: TogetherEvent): Result<Unit> =
        Result.failure(UnsupportedOperationException("尚未接入一起听服务"))
}