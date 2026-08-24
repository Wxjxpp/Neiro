package com.wxjxpp.neiro.core.together

import com.wxjxpp.neiro.core.model.TogetherConnectionState
import com.wxjxpp.neiro.core.model.TogetherEvent
import com.wxjxpp.neiro.core.model.TogetherMember
import com.wxjxpp.neiro.core.model.TogetherRoom
import com.wxjxpp.neiro.core.data.DataStoreSettingsRepository
import com.wxjxpp.neiro.core.net.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一起听传输层 · Neiro-LIT 服务端实现（HTTP 轮询）。
 *
 * 兼容 Cloudflare Workers 与 Vercel 双后端（协议一致，见 Neiro-LIT 仓库 README）。
 * - 同步：轮询 GET /state（3s），按 [version] 做乐观更新检测
 * - 控制：POST /control（房主直接生效；听众发 REQUEST_*)
 * - 进度校正：以快照的 expectedPositionMs + serverNowMs 推算，偏差 > 800ms 才 seek，
 *   避免与本地播放互相打架
 * - 会话凭据持久化在 DataStore，杀进程后可用 memberSecret 重连
 */
class LitTogetherTransport(
    private val settings: DataStoreSettingsRepository,
    private val http: HttpClient,
    private val scope: CoroutineScope,
) : TogetherTransport {

    override val connectionState = MutableStateFlow(TogetherConnectionState.Disconnected)
    override val room = MutableStateFlow<TogetherRoom?>(null)
    override val serverTimeOffsetMs = MutableStateFlow(0L)
    private val _events = MutableSharedFlow<TogetherEvent>(extraBufferCapacity = 32)
    override fun events(): Flow<TogetherEvent> = _events

    /** 最新权威房态原始 JSON（UI 展示队列/成员用）。 */
    val roomStateJson = MutableStateFlow<JSONObject?>(null)

    /** 设置镜像流：UI 表单回显用。 */
    val serverUrlFlow = MutableStateFlow("")
    val nicknameFlow = MutableStateFlow("")

    /** 创建房间时服务端下发的邀请密钥（仅本机持有，用于复制邀请）。 */
    var lastJoinSecret: String = ""
        private set

    // ---- 本机身份与会话 ----
    var serverUrl: String = ""
        private set
    var nickname: String = ""
        private set
    private var roomId: String = ""
    private var memberId: String = ""
    private var memberSecret: String = ""
    private var token: String = ""
    private var isController = false

    /** 房间当前曲目（供 UI/播放引擎消费）。 */
    val currentTrackJson = MutableStateFlow<JSONObject?>(null)

    private var pollJob: Job? = null
    private var lastVersion = -1L

    companion object {
        /** 位置偏差超过该值才触发 seek 校正。 */
        const val DRIFT_TOLERANCE_MS = 800L
        const val POLL_INTERVAL_MS = 3000L
    }

    // ================= TogetherTransport 接口实现 =================

    /**
     * 接口约定方法。name 格式灵活：
     * - "https://host|昵称"（完整指定）
     * - "昵称"（复用已保存的服务端地址）
     */
    override suspend fun createRoom(name: String): Result<TogetherRoom> {
        val parts = name.split('|', limit = 2)
        val url = parts.getOrNull(0)?.takeIf { it.startsWith("http") } ?: serverUrlFlow.value
        val nick = parts.getOrNull(1) ?: nicknameFlow.value
        return createRoomAt(url, nick).mapCatching {
            room.value ?: throw IllegalStateException("房间状态缺失")
        }
    }

    /** 接口约定方法。roomId 格式："房间号" 或 "房间号|邀请密钥"。 */
    override suspend fun joinRoom(roomId: String): Result<TogetherRoom> {
        val parts = roomId.split('|', limit = 2)
        val id = parts.getOrNull(0).orEmpty()
        val secret = parts.getOrNull(1).orEmpty()
        return joinRoomAt(
            serverUrlFlow.value,
            id,
            nicknameFlow.value,
            secret,
        ).mapCatching {
            room.value ?: throw IllegalStateException("房间状态缺失")
        }
    }

    override suspend fun leaveRoom() = leaveRoomAndClear()

    /** 把领域事件翻译成服务端控制指令。 */
    override suspend fun send(event: TogetherEvent): Result<Unit> = when (event) {
        is TogetherEvent.Play -> sendControl("PLAY") { put("positionMs", event.positionMs) }
        is TogetherEvent.Pause -> sendControl("PAUSE") { put("positionMs", event.positionMs) }
        is TogetherEvent.Seek -> sendControl("SEEK") { put("positionMs", event.positionMs) }
        else -> Result.failure(UnsupportedOperationException("该事件不支持直接发送：$event"))
    }

    // ================= 会话管理 =================

    /** 从 DataStore 恢复会话（应用启动时调用一次）。 */
    suspend fun restoreSession() {
        val session = settings.observeTogetherSession().firstOrNull() ?: return
        val (r, m, s, t) = listOf(
            session.getOrNull(0).orEmpty(),
            session.getOrNull(1).orEmpty(),
            session.getOrNull(2).orEmpty(),
            session.getOrNull(3).orEmpty(),
        )
        if (r.isNotEmpty() && m.isNotEmpty()) {
            roomId = r; memberId = m; memberSecret = s; token = t
            serverUrl = settings.observeTogetherServerUrl().firstOrNull().orEmpty()
            nickname = settings.observeTogetherNickname().firstOrNull().orEmpty()
            connectionState.value = TogetherConnectionState.Reconnecting
            startPolling()
        }
    }

    /**
     * 创建房间（成为房主/控制者）。
     * @return 成功时返回房间号；失败返回错误信息。
     */
    suspend fun createRoomAt(baseUrl: String, nick: String): Result<String> {
        serverUrl = baseUrl.trim().trimEnd('/')
        nickname = nick
        val body = JSONObject().put("nickname", nick)
        val resp = runCatching { http.postJson("$serverUrl/api/rooms", body.toString()) }
            .getOrElse { return Result.failure(it) }
        if (!resp.isSuccessful) return Result.failure(parseError(resp.body))
        val json = JSONObject(resp.body)
        roomId = json.getString("roomId")
        memberId = json.getString("memberId")
        isController = json.optString("role") == "controller"
        memberSecret = json.optString("memberSecret")
        lastJoinSecret = json.optString("joinSecret")
        token = json.getString("token")
        serverUrlFlow.value = serverUrl
        nicknameFlow.value = nickname
        persistSession()
        onSnapshot(json.getJSONObject("state"))
        startPolling()
        return Result.success(roomId)
    }

    /**
     * 加入房间（明确指定房间号）。secret 传邀请密钥；已有成员密钥时优先重连。
     */
    suspend fun joinRoomAt(baseUrl: String, id: String, nick: String, secret: String): Result<String> {
        serverUrl = baseUrl.trim().trimEnd('/')
        nickname = nick
        roomId = id.trim().uppercase()
        // 已有会话凭据优先重连（服务端识别 memberSecret 不触发暂停）
        val useSecret = memberSecret.ifEmpty { secret }
        val body = JSONObject()
            .put("nickname", nick)
            .put("secret", useSecret)
        val resp = runCatching {
            http.postJson("$serverUrl/api/rooms/$roomId/join", body.toString())
        }.getOrElse { return Result.failure(it) }
        if (!resp.isSuccessful) return Result.failure(parseError(resp.body))
        val json = JSONObject(resp.body)
        memberId = json.getString("memberId")
        isController = json.optString("role") == "controller"
        memberSecret = json.optString("memberSecret")
        token = json.getString("token")
        persistSession()
        onSnapshot(json.getJSONObject("state"))
        startPolling()
        return Result.success(roomId)
    }

    suspend fun leaveRoomAndClear() {
        stopPolling()
        runCatching {
            http.postJson(
                "$serverUrl/api/rooms/$roomId/leave",
                "{}",
                mapOf("Authorization" to "Bearer $token"),
            )
        }
        settings.clearTogetherSession()
        resetLocal()
    }

    private fun persistSession() {
        scope.launch {
            settings.setTogetherServerUrl(serverUrl)
            settings.setTogetherNickname(nickname)
            settings.setTogetherSession(roomId, memberId, memberSecret, token)
        }
    }

    private fun resetLocal() {
        roomId = ""; memberId = ""; memberSecret = ""; token = ""; isController = false
        lastVersion = -1
        room.value = null
        roomStateJson.value = null
        currentTrackJson.value = null
        connectionState.value = TogetherConnectionState.Disconnected
    }

    // ================= 轮询同步 =================

    private fun startPolling() {
        stopPolling()
        pollJob = scope.launch {
            while (isActive && roomId.isNotEmpty()) {
                pollOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun pollOnce() {
        if (token.isEmpty()) return
        val resp = runCatching {
            http.get(
                "$serverUrl/api/rooms/$roomId/state",
                mapOf("Authorization" to "Bearer $token"),
            )
        }.getOrElse {
            connectionState.value = TogetherConnectionState.Reconnecting
            return
        }
        when {
            resp.isSuccessful -> {
                connectionState.value = TogetherConnectionState.Connected
                val json = JSONObject(resp.body)
                onSnapshot(json.getJSONObject("state"))
            }
            resp.statusCode == 404 -> {
                // 房间已关闭
                stopPolling()
                settings.clearTogetherSession()
                resetLocal()
                _events.tryEmit(TogetherEvent.MemberChanged(emptyList()))
            }
            else -> connectionState.value = TogetherConnectionState.Reconnecting
        }
    }

    /** 应用权威快照：更新本地模型、发事件、算时钟偏移。（轮询串行执行，无需加锁） */
    private fun onSnapshot(state: JSONObject) {
        val version = state.optLong("version", -1L)
        if (version == lastVersion) return
        val firstSync = lastVersion == -1L || version < lastVersion
        lastVersion = version

        val nowServer = state.optLong("serverNowMs", System.currentTimeMillis())
        serverTimeOffsetMs.value = nowServer - System.currentTimeMillis()

        // 成员列表
        val membersJson = state.optJSONObject("members") ?: JSONObject()
        val controllerId = state.optString("controllerId")
        val members = membersJson.keys().asSequence().map { id ->
            val m = membersJson.getJSONObject(id)
            TogetherMember(
                id = id,
                name = m.optString("nickname"),
                isHost = id == controllerId,
            )
            }.toList()

        room.value = TogetherRoom(
            id = state.optString("roomId"),
            name = "房间 ${state.optString("roomId")}",
            members = members,
            hostId = controllerId,
        )
        roomStateJson.value = state

        val playback = state.optJSONObject("playback") ?: return
        val track = playback.optJSONObject("track")
        currentTrackJson.value = track

        val playing = playback.optBoolean("playing", false)
        val expectedPos = state.optLong("expectedPositionMs", 0L)
        val atServerTime = nowServer

        // 曲目变化 → QueueChanged（含当前曲目）
        if (firstSync || track != null) {
            val queue = playback.optJSONArray("queue") ?: JSONArray()
            val songIds = (0 until queue.length()).map { i ->
                queue.getJSONObject(i).optString("stableKey")
                }
            scope.launch { _events.emit(TogetherEvent.QueueChanged(songIds)) }
            }

        // 播放状态事件（简化：以快照为准广播 Play/Pause）
        if (track != null) {
            val songId = "${track.optString("sourceId")}:${track.optString("songId")}"
            if (playing) {
                scope.launch {
                    _events.emit(TogetherEvent.Play(songId, expectedPos, atServerTime))
                }
            } else {
                scope.launch { _events.emit(TogetherEvent.Pause(expectedPos, atServerTime)) }
            }
        }
    }

    // ================= 控制指令 =================

    /**
     * 提交控制事件。房主发 PLAY/PAUSE/SEEK/SET_TRACK 等；
     * 听众自动转 REQUEST_*（服务端按 allowMemberControl 门控）。
     */
    suspend fun sendControl(type: String, payload: JSONObject.() -> Unit = {}): Result<Unit> {
        if (token.isEmpty()) return Result.failure(IllegalStateException("不在房间中"))
        val evtType = if (!isController && !type.startsWith("REQUEST_")) "REQUEST_$type" else type
        val event = JSONObject().put("type", evtType).apply(payload)
        val resp = runCatching {
            http.postJson(
                "$serverUrl/api/rooms/$roomId/control",
                JSONObject().put("event", event).toString(),
                mapOf("Authorization" to "Bearer $token"),
            )
        }.getOrElse { return Result.failure(it) }
        if (!resp.isSuccessful) return Result.failure(parseError(resp.body))
        onSnapshot(JSONObject(resp.body).getJSONObject("state"))
        return Result.success(Unit)
    }

    // ================= 工具 =================

    private fun parseError(body: String): Exception =
        runCatching { Exception(JSONObject(body).optString("error")) }
            .getOrDefault(Exception("HTTP 错误"))

    /** 由曲目 JSON 还原 Song 的稳定键。 */
    fun stableKeyOf(sourceId: String, songId: String) = "$sourceId:$songId"
}