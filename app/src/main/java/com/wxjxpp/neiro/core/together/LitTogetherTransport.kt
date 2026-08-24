package com.wxjxpp.neiro.core.together

import com.wxjxpp.neiro.core.data.DataStoreSettingsRepository
import com.wxjxpp.neiro.core.model.TogetherConnectionState
import com.wxjxpp.neiro.core.model.TogetherEvent
import com.wxjxpp.neiro.core.model.TogetherMember
import com.wxjxpp.neiro.core.model.TogetherRoom
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
 * 一起听传输层 · Neiro-LIT 服务端实现 v2「民主房间」（HTTP 轮询）。
 *
 * 兼容 Cloudflare Workers 与 Vercel 双后端（协议一致，见 Neiro-LIT 仓库 README）。
 * - 同步：轮询 GET /state（3s），按 version 做乐观更新检测；轮询即心跳。
 * - 控制：POST /control（房主直接生效；群友发 REQUEST_*）。
 * - 进度校正：以快照的 expectedPositionMs + serverNowMs 推算，偏差 > 800ms 才 seek。
 * - 会话凭据持久化在 DataStore（五元组），杀进程后自动恢复并重连。
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

    /** 最新权威房态原始 JSON（UI 展示队列/投票/聊天用）。 */
    val roomStateJson = MutableStateFlow<JSONObject?>(null)
    /** 设置镜像流：UI 表单回显用。 */
    val serverUrlFlow = MutableStateFlow("")
    val nicknameFlow = MutableStateFlow("")

    /** 创建房间时服务端下发的邀请密钥（持久化，杀进程不丢）。 */
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

    /** 客户端事件序号（幂等/乱序防护，服务端要求递增）。 */
    private var clientSequence = 0L

    private var pollJob: Job? = null
    private var lastVersion = -1L

    companion object {
        /** 位置偏差超过该值才触发 seek 校正。 */
        const val DRIFT_TOLERANCE_MS = 800L
        const val POLL_INTERVAL_MS = 3000L
        /** URL 加载超时（超过即报 TRACK_ERROR 兜底切歌）。 */
        const val LOAD_TIMEOUT_MS = 5000L

        /** djb2 URL 哈希，与服务端一致（去重键）。 */
        fun urlHash(url: String): String {
            var h = 5381L
            for (ch in url.trim()) h = ((h shl 5) + h + ch.code) and 0xFFFFFFFFL
            return java.lang.Long.toHexString(h).padStart(8, '0')
        }
    }

    /** 当前是否以房主身份在房内（供上报桥与 UI 判断）。 */
    val isControllerInRoom: Boolean
        get() = token.isNotEmpty() && isController

    /** 本机在房内的成员 ID（踢人按钮排除自己用）。 */
    val selfMemberId: String get() = memberId

    // ================= TogetherTransport 接口实现 =================

    override suspend fun createRoom(name: String): Result<TogetherRoom> {
        val parts = name.split('|', limit = 2)
        val url = parts.getOrNull(0)?.takeIf { it.startsWith("http") } ?: serverUrlFlow.value
        val nick = parts.getOrNull(1) ?: nicknameFlow.value
        return createRoomAt(url, nick).mapCatching {
            room.value ?: throw IllegalStateException("房间状态缺失")
        }
    }

    override suspend fun joinRoom(roomId: String): Result<TogetherRoom> {
        val parts = roomId.split('|', limit = 2)
        return joinRoomAt(serverUrlFlow.value, parts[0], nicknameFlow.value, parts.getOrElse(1) { "" })
            .mapCatching { room.value ?: throw IllegalStateException("房间状态缺失") }
    }

    override suspend fun leaveRoom() = leaveRoomAndClear()

    override suspend fun send(event: TogetherEvent): Result<Unit> = when (event) {
        is TogetherEvent.Play -> sendControl("PLAY") { put("positionMs", event.positionMs) }
        is TogetherEvent.Pause -> sendControl("PAUSE") { put("positionMs", event.positionMs) }
        is TogetherEvent.Seek -> sendControl("SEEK") { put("positionMs", event.positionMs) }
        else -> Result.failure(UnsupportedOperationException("该事件不支持直接发送：$event"))
    }

    // ================= 会话管理 =================

    /**
     * 从 DataStore 恢复会话（应用启动时调用一次）。
     * 有会话凭据则进入重连轮询；凭据失效会在轮询收到 404/401 时自动清理回大厅。
     */
    suspend fun restoreSession() {
        if (token.isNotEmpty()) return // 已在会话中
        val session = settings.observeTogetherSession().firstOrNull() ?: return
        val savedRoom = session.getOrNull(0).orEmpty()
        val savedMember = session.getOrNull(1).orEmpty()
        if (savedRoom.isEmpty() || savedMember.isEmpty()) return
        roomId = savedRoom
        memberId = savedMember
        memberSecret = session.getOrNull(2).orEmpty()
        token = session.getOrNull(3).orEmpty()
        lastJoinSecret = session.getOrNull(4).orEmpty()
        serverUrl = settings.observeTogetherServerUrl().firstOrNull().orEmpty()
        nickname = settings.observeTogetherNickname().firstOrNull().orEmpty()
        serverUrlFlow.value = serverUrl
        nicknameFlow.value = nickname
        if (serverUrl.isNotEmpty() && token.isNotEmpty()) {
            connectionState.value = TogetherConnectionState.Reconnecting
            startPolling()
        }
    }

    /** 创建房间（成为房主）。成功返回房间号。 */
    suspend fun createRoomAt(baseUrl: String, nick: String): Result<String> {
        serverUrl = baseUrl.trim().trimEnd('/')
        nickname = nick
        val body = JSONObject().put("nickname", nick)
        val resp = runCatching { http.postJson("$serverUrl/api/rooms", body.toString()) }
            .getOrElse { return Result.failure(it) }
        if (!resp.isSuccessful) return Result.failure(parseError(resp.body))
        val json = JSONObject(resp.body)
        applyIdentity(json, controllerDefault = true)
        persistSession()
        onSnapshot(json.getJSONObject("state"))
        startPolling()
        return Result.success(roomId)
    }

    /** 加入房间。secret 传邀请密钥；已有成员密钥时优先重连。 */
    suspend fun joinRoomAt(baseUrl: String, id: String, nick: String, secret: String): Result<String> {
        serverUrl = baseUrl.trim().trimEnd('/')
        nickname = nick
        roomId = id.trim().uppercase()
        val useSecret = memberSecret.ifEmpty { secret }
        val body = JSONObject().put("nickname", nick).put("secret", useSecret)
        val resp = runCatching {
            http.postJson("$serverUrl/api/rooms/$roomId/join", body.toString())
        }.getOrElse { return Result.failure(it) }
        if (!resp.isSuccessful) return Result.failure(parseError(resp.body))
        val json = JSONObject(resp.body)
        applyIdentity(json, controllerDefault = false)
        persistSession()
        onSnapshot(json.getJSONObject("state"))
        startPolling()
        return Result.success(roomId)
    }

    private fun applyIdentity(json: JSONObject, controllerDefault: Boolean) {
        roomId = json.optString("roomId", roomId)
        memberId = json.getString("memberId")
        isController = when {
            json.has("role") -> json.optString("role") == "controller"
            else -> controllerDefault
        }
        memberSecret = json.optString("memberSecret", memberSecret)
        if (json.has("joinSecret")) lastJoinSecret = json.optString("joinSecret")
        token = json.getString("token")
        serverUrlFlow.value = serverUrl
        nicknameFlow.value = nickname
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
            settings.setTogetherSession(roomId, memberId, memberSecret, token, lastJoinSecret)
        }
    }

    private fun resetLocal() {
        roomId = ""; memberId = ""; memberSecret = ""; token = ""; isController = false
        clientSequence = 0
        lastVersion = -1
        room.value = null
        roomStateJson.value = null
        currentTrackJson.value = null
        connectionState.value = TogetherConnectionState.Disconnected
    }

    // ================= 控制指令 =================

    private suspend fun sendControl(
        type: String,
        payload: JSONObject.() -> Unit = {},
    ): Result<Unit> {
        if (token.isEmpty()) return Result.failure(IllegalStateException("不在房间中"))
        clientSequence++
        // 全员开放事件（投票/聊天/加歌/无效源上报）直接发；
        // 其余控制类事件由群友发时加 REQUEST_ 前缀，服务端按 allowMemberControl 门控。
        val openEvents = setOf("VOTE", "CHAT", "ADD_SONG", "TRACK_ERROR")
        val evtType = if (!isController && type !in openEvents && !type.startsWith("REQUEST_")) {
            "REQUEST_$type"
        } else {
            type
        }
        val event = JSONObject()
            .put("type", evtType)
            .put("clientSequence", clientSequence)
            .apply(payload)
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

    /** 上报当前播放曲目（仅房主调用；群友调用会被服务端拒绝）。 */
    suspend fun publishCurrentTrack(track: JSONObject, positionMs: Long, playing: Boolean): Result<Unit> =
        sendControl("SET_TRACK") {
            put("track", track)
            put("positionMs", positionMs)
            put("shouldPlay", playing)
        }

    /** 房主上报播放/暂停/进度（不含切歌）。 */
    suspend fun publishPlayback(positionMs: Long, playing: Boolean): Result<Unit> =
        sendControl(if (playing) "PLAY" else "PAUSE") { put("positionMs", positionMs) }

    /** 加歌：URL 直链（全员可用，受 lockAddSongs 门控；服务端先探测可用性）。 */
    suspend fun addSongByUrl(url: String, title: String, artist: String = "", cover: String = ""): Result<Unit> {
        val track = JSONObject()
            .put("sourceId", "url")
            .put("songId", urlHash(url))
            .put("url", url.trim())
            .put("title", title)
            .put("artist", artist)
            .put("cover", cover)
        return sendControl("ADD_SONG") { put("track", track) }
    }

    /**
     * 点歌：从聚合搜索结果添加平台曲目（payload 随当前曲目透传给听众取流）。
     * 全员可用，受 lockAddSongs 门控。
     */
    suspend fun addSongFromPlatform(song: com.wxjxpp.neiro.core.model.Song): Result<Unit> {
        val loc = song.location as? com.wxjxpp.neiro.core.model.MediaLocation.Remote
            ?: return Result.failure(IllegalArgumentException("本地歌曲不能加入房间"))
        val track = JSONObject()
            .put("sourceId", loc.sourceId)
            .put("songId", loc.songId)
            .put("title", song.title)
            .put("artist", song.artistName)
            .put("album", song.albumTitle)
            .put("durationMs", song.durationMs)
            .put("cover", song.coverUri.orEmpty())
            .put("payload", loc.payload.orEmpty())
        return sendControl("ADD_SONG") { put("track", track) }
    }

    /** 投票：每人每首歌限投一次；踩票占比达阈值自动切歌。 */
    suspend fun vote(voteUp: Boolean): Result<Unit> =
        sendControl("VOTE") { put("vote", if (voteUp) "up" else "down") }

    /** 弹幕/聊天（全员可用，服务端限频 1.5s）。 */
    suspend fun chat(text: String): Result<Unit> =
        sendControl("CHAT") { put("text", text.take(200)) }

    /** 踢人（仅房主）。 */
    suspend fun kick(targetMemberId: String): Result<Unit> {
        if (token.isEmpty()) return Result.failure(IllegalStateException("不在房间中"))
        val resp = runCatching {
            http.postJson(
                "$serverUrl/api/rooms/$roomId/kick",
                JSONObject().put("targetId", targetMemberId).toString(),
                mapOf("Authorization" to "Bearer $token"),
            )
        }.getOrElse { return Result.failure(it) }
        if (!resp.isSuccessful) return Result.failure(parseError(resp.body))
        onSnapshot(JSONObject(resp.body).getJSONObject("state"))
        return Result.success(Unit)
    }

    /** 无效源兜底上报：URL 失效或加载超时(>5s)。 */
    suspend fun reportTrackError(): Result<Unit> = sendControl("TRACK_ERROR")

    suspend fun requestNext(): Result<Unit> = sendControl("NEXT")
    suspend fun requestPrev(): Result<Unit> = sendControl("PREV")

    /** 移除队列中的曲目（房主；删当前曲目会自动切下一首或待机）。 */
    suspend fun removeSong(stableKey: String): Result<Unit> =
        sendControl("REMOVE_SONG") { put("stableKey", stableKey) }

    /** 在队列里找 stableKey 对应的曲目 JSON。 */
    fun findInQueue(stableKey: String): JSONObject? {
        val state = roomStateJson.value ?: return null
        val queue = state.optJSONObject("playback")?.optJSONArray("queue") ?: return null
        for (i in 0 until queue.length()) {
            val t = queue.getJSONObject(i)
            if (t.optString("stableKey") == stableKey) return t
        }
        return null
    }

        /** 与服务端 djb2 一致的 URL 哈希（去重键）见 [Companion.urlHash]。 */

    private fun parseError(body: String): Exception =
        runCatching { Exception(JSONObject(body).optString("error")) }
            .getOrDefault(Exception("HTTP 错误"))

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
                onSnapshot(JSONObject(resp.body).getJSONObject("state"))
            }
            resp.statusCode == 403 -> {
                // 被房主移出房间：提示并回到大厅
                stopPolling()
                settings.clearTogetherSession()
                resetLocal()
                _events.tryEmit(TogetherEvent.MemberChanged(emptyList()))
                _events.tryEmit(TogetherEvent.Kicked)
            }
            resp.statusCode == 404 || resp.statusCode == 401 -> {
                // 房间已销毁 / 凭据失效：清理会话回到大厅
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
        val firstSync = lastVersion == -1L
        lastVersion = version

        val nowServer = state.optLong("serverNowMs", System.currentTimeMillis())
        serverTimeOffsetMs.value = nowServer - System.currentTimeMillis()

        // 成员列表 + 本机角色确认（重连后角色可能变化）
        val membersJson = state.optJSONObject("members") ?: JSONObject()
        val controllerId = state.optString("controllerId")
        if (memberId.isNotEmpty() && membersJson.has(memberId)) {
            isController = memberId == controllerId
        }
        val members = membersJson.keys().asSequence().map { id ->
            val m = membersJson.getJSONObject(id)
            TogetherMember(id = id, name = m.optString("nickname"), isHost = id == controllerId)
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

        // 队列变化 → QueueChanged
        if (firstSync || track != null) {
            val queue = playback.optJSONArray("queue") ?: JSONArray()
            val songKeys = (0 until queue.length()).map { i ->
                queue.getJSONObject(i).optString("stableKey")
            }
            scope.launch { _events.emit(TogetherEvent.QueueChanged(songKeys)) }
        }

        // 曲目/播放状态 → Play/Pause（songId 用 stableKey 保证跨端一致）
        if (track != null) {
            val songKey = track.optString("stableKey").ifEmpty {
                "${track.optString("sourceId")}:${track.optString("songId")}"
            }
            if (playing) {
                scope.launch { _events.emit(TogetherEvent.Play(songKey, expectedPos, nowServer)) }
            } else {
                scope.launch { _events.emit(TogetherEvent.Pause(expectedPos, nowServer)) }
            }
        } else {
            scope.launch { _events.emit(TogetherEvent.Pause(0L, nowServer)) }
        }
    }
}