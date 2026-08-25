@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.wxjxpp.neiro.feature.together

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ThumbDownAlt
import androidx.compose.material.icons.rounded.ThumbUpAlt
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wxjxpp.neiro.core.model.Album
import com.wxjxpp.neiro.core.model.Artist
import com.wxjxpp.neiro.core.model.MediaLocation
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.core.model.TogetherConnectionState
import com.wxjxpp.neiro.core.player.PlayerController
import com.wxjxpp.neiro.core.together.LitTogetherTransport
import com.wxjxpp.neiro.ui.components.ConnectedChoiceGroup
import com.wxjxpp.neiro.ui.components.SongCover
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 一起听页面（v2 民主房间）。
 *
 * 未入房：填服务端地址 / 昵称 → 创建房间 或 加入房间（房间号+邀请密钥）。
 * 已入房：邀请密钥卡片、投票进度条、加歌、队列、聊天弹幕、成员管理（房主可踢人）。
 * 同步逻辑：监听 [LitTogetherTransport.currentTrackJson]，与本地播放不一致时自动切歌；
 * 房主本机的播放由 AppContainer 桥接上报（SET_TRACK/PLAY/PAUSE），听众跟随服务端快照。
 */
@Composable
fun TogetherScreen(
    transport: LitTogetherTransport,
    player: PlayerController,
    onMessage: (String) -> Unit,
    search: com.wxjxpp.neiro.core.search.OnlineSearchRepository,
    resolveUrl: suspend (com.wxjxpp.neiro.core.model.Song) -> String?,
    modifier: Modifier = Modifier,
) {
    val connection by transport.connectionState.collectAsState()
    val room by transport.room.collectAsState()
    val inRoom = room != null && connection != TogetherConnectionState.Disconnected

    // 被踢提示（服务端 403「你已被移出房间」触发）
    LaunchedEffect(Unit) {
        transport.events().collect { e ->
            if (e == com.wxjxpp.neiro.core.model.TogetherEvent.Kicked) {
                onMessage("你已被房主移出房间")
            }
        }
    }

    if (inRoom) {
        RoomView(transport, player, onMessage, search, resolveUrl, modifier)
    } else {
        LobbyView(transport, onMessage, modifier)
    }
}

// ================= 大厅 =================

// ================= 大厅 =================
/** 解析邀请分享文本："XXX-XXX|密钥"；兼容旧版「房间ID：X 密钥：Y」。返回 (roomId, secret) 或 null。 */
internal fun parseInviteMessage(text: String): Pair<String, String>? {
    val t = text.trim()
    if (t.isEmpty()) return null
    if (t.contains("|")) {
        val head = t.substringBefore("|").trim()
        val secret = t.substringAfter("|", "").trim()
        val digits = head.filter(Char::isDigit)
        if (digits.length in 4..12 && secret.isNotEmpty()) return digits to secret
        return null
    }
    val idM = Regex("(?:房间ID|房间号)[:：]\\s*([A-Za-z0-9\\-]+)").find(t)
    val secM = Regex("密钥[:：]\\s*([A-Za-z0-9]+)").find(t)
    if (idM != null && secM != null) {
        return idM.groupValues[1].replace("-", "") to secM.groupValues[1]
    }
    // 兜底：整段就是「房号 密钥」两段式
    val parts = t.split(Regex("\\s+"))
    if (parts.size == 2 && parts[0].all(Char::isDigit) && parts[0].length in 4..12) {
        return parts[0] to parts[1]
    }
    return null
}
/** 房间号展示格式：123456 → 123-456 */
internal fun formatRoomId(id: String): String =
    if (id.length == 6) "${id.slice(0..2)}-${id.slice(3..5)}" else id

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)
@Composable
private fun LobbyView(
    transport: LitTogetherTransport,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val savedUrl by transport.serverUrlFlow.collectAsState()
    val savedNick by transport.nicknameFlow.collectAsState()
    // 服务端地址不再预置官方实例（CF 免费额度有限），由用户自部署或向房主索取
    var url by remember(savedUrl) { mutableStateOf(savedUrl) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var nick by remember(savedNick) { mutableStateOf(savedNick) }
    // 底部 Sheet 开关：创建房间（填房间名）/ 加入房间（粘贴邀请消息）/ 汉堡菜单
    var showCreateSheet by remember { mutableStateOf(false) }
    var showJoinSheet by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var roomNameInput by remember { mutableStateOf("") }
    var inviteInput by remember { mutableStateOf("") }

    Box(modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            // 标题行 + 右上角汉堡菜单
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "一起听",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showMenuSheet = true }) {
                    Icon(Icons.Rounded.Menu, contentDescription = "更多选项")
                }
            }
            Text(
                "和好友实时同步听歌。群友可以搜索点歌、投票切歌、发弹幕；房主拥有全部控制权。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    transport.saveServerUrl(it) // 输入即持久化，重启不丢
                },
                label = { Text("一起听服务器（必填）") }, singleLine = true,
                placeholder = { Text("https://你的Worker地址.workers.dev") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = nick,
                onValueChange = {
                    nick = it.take(24)
                    transport.saveNickname(nick) // 输入即持久化，重启不丢
                },
                label = { Text("显示昵称（1-24 位中文/字母/数字）") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            // 并排操作：加入=MD3E 主色实心蓝，创建=浅色描边
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showJoinSheet = true },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("加入房间", style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = { showCreateSheet = true },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(),
                ) { Text("创建房间", style = MaterialTheme.typography.labelLarge) }
            }
            Text(
                "加入房间：把房主分享的邀请消息整段粘贴即可",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 部署引导：CF 免费额度有限，不提供公共实例
            androidx.compose.material3.TextButton(onClick = { showHelpDialog = true }) {
                Text("如何获取客户端地址？")
            }
            Spacer(Modifier.height(24.dp))
        }
        // 创建/加入是网络操作且 CF 冷启动可能较慢：全屏加载层（MD3E LoadingIndicator）
        if (busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.LoadingIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("正在连接服务器…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

        // ---- 部署引导弹窗 ----
    if (showHelpDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showHelpDialog = false }) {
                    Text("我知道了")
                }
            },
            title = { Text("如何获取一起听服务器地址？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "服务器地址需要自己部署，或者向房主索要。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "部署请看：",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/Wxjxpp/wxjxpp-neiro-lit")
                            showHelpDialog = false
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "github.com/Wxjxpp/wxjxpp-neiro-lit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/Wxjxpp/wxjxpp-neiro-lit")
                            showHelpDialog = false
                        },
                    )
                }
            },
        )
    }

    // ---- 创建房间 Sheet：仅需输入房间名称 ----
    if (showCreateSheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { if (!busy) showCreateSheet = false }) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("创建房间", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = roomNameInput, onValueChange = { roomNameInput = it.take(30) },
                    label = { Text("房间名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (url.isBlank() || !url.startsWith("http")) {
                            onMessage("请先填写一起听服务器地址"); return@Button
                        }
                        if (nick.isBlank()) { onMessage("请先填写显示昵称"); return@Button }
                        busy = true
                        scope.launch {
                            val r = transport.createRoomAt(url, nick.trim(), roomNameInput.trim())
                            busy = false
                            r.fold({
                                showCreateSheet = false
                                roomNameInput = ""
                                onMessage("房间已创建：$it")
                            }, { onMessage(it.message ?: "创建失败") })
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (busy) androidx.compose.material3.LoadingIndicator(modifier = Modifier.height(24.dp))
                    else Text("创建并进入", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // ---- 加入房间 Sheet：整段粘贴邀请消息即可 ----
    if (showJoinSheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { if (!busy) showJoinSheet = false }) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("加入房间", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = inviteInput, onValueChange = { inviteInput = it },
                    label = { Text("粘贴邀请消息") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Text(
                    "示例：我在Neiro听歌，复制消息和我一起听\n123-456|密钥",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        if (url.isBlank() || !url.startsWith("http")) {
                            onMessage("请先填写一起听服务器地址"); return@Button
                        }
                        if (nick.isBlank()) { onMessage("请先填写显示昵称"); return@Button }
                        val parsed = parseInviteMessage(inviteInput)
                        if (parsed == null) {
                            onMessage("邀请消息格式不对，请完整复制房主分享的内容"); return@Button
                        }
                        val (rid, sec) = parsed
                        if (sec.isEmpty()) { onMessage("邀请消息里缺少密钥，请让房主重新复制"); return@Button }
                        busy = true
                        scope.launch {
                            val r = transport.joinRoomAt(url, rid, nick.trim(), sec)
                            busy = false
                            r.fold({
                                showJoinSheet = false
                                inviteInput = ""
                                onMessage("已加入房间 $it")
                            }, { onMessage(it.message ?: "加入失败") })
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (busy) androidx.compose.material3.LoadingIndicator(modifier = Modifier.height(24.dp))
                    else Text("加入", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // ---- 汉堡菜单 Sheet：服务器/昵称概览 + 重置唯一身份 ----
    if (showMenuSheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showMenuSheet = false }) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("更多选项", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("一起听服务器") },
                    supportingContent = { Text(url.ifEmpty { "未设置" }) },
                    leadingContent = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                )
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("显示昵称") },
                    supportingContent = { Text(nick.ifEmpty { "未设置" }) },
                    leadingContent = { Icon(Icons.Rounded.Person, contentDescription = null) },
                )
                androidx.compose.material3.HorizontalDivider()
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("重置唯一身份标识") },
                    supportingContent = { Text("生成新的 32 位设备身份；旧身份关联的房间会话将作废") },
                    leadingContent = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                    modifier = Modifier.clickable {
                        scope.launch {
                            val newUid = transport.resetIdentity()
                            showMenuSheet = false
                            onMessage("唯一身份已重置：${newUid.take(8)}…")
                        }
                    },
                )
            }
        }
    }
}

// ================= 房间内 =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomView(
    transport: LitTogetherTransport,
    player: PlayerController,
    onMessage: (String) -> Unit,
    search: com.wxjxpp.neiro.core.search.OnlineSearchRepository,
    resolveUrl: suspend (com.wxjxpp.neiro.core.model.Song) -> String?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val connection by transport.connectionState.collectAsState()
    val room by transport.room.collectAsState()
    val state by transport.roomStateJson.collectAsState()
    val clipboard = LocalClipboardManager.current
    val r = room ?: return
    val isHost = transport.isControllerInRoom
    val curTrack by transport.currentTrackJson.collectAsState()
    val pb = state?.optJSONObject("playback")
    val votesSummary = state?.optJSONObject("voteSummary")
    val online = state?.optInt("onlineCount", r.members.size)?.coerceAtLeast(1) ?: r.members.size
    val up = votesSummary?.optInt("up", 0) ?: 0
    val down = votesSummary?.optInt("down", 0) ?: 0
    val threshold = votesSummary?.optInt("threshold", 50) ?: 50
    val myId = transport.selfMemberId
    val votedUp = votesSummary?.optJSONArray("upIds")?.let { a -> (0 until a.length()).any { a.optString(it) == myId } } == true
    val votedDown = votesSummary?.optJSONArray("downIds")?.let { a -> (0 until a.length()).any { a.optString(it) == myId } } == true
    // ---- 页面交互状态：菜单/弹层/确认框/加载 ----
    var menuOpen by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var confirmKickTarget by remember { mutableStateOf<com.wxjxpp.neiro.core.model.TogetherMember?>(null) }
    var confirmCloseRoom by remember { mutableStateOf(false) }
    var kicking by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var votingUp by remember { mutableStateOf(false) }
    var votingDown by remember { mutableStateOf(false) }
    // ---- 点歌搜索状态 ----
    var keyword by remember { mutableStateOf("") }
    var platformId by remember { mutableStateOf(com.wxjxpp.neiro.core.search.OnlineSearchRepository.ALL) }
    var results by remember { mutableStateOf<List<com.wxjxpp.neiro.core.model.Song>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var addingKey by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        searching = true
        scope.launch {
            val res = search.search(kw, platformId)
            results = res.songs
            searching = false
            searched = true
        }
    }

    fun castVote(upVote: Boolean) {
        if (votedUp || votedDown) { onMessage("每人每首歌限投一次"); return }
        if (upVote) votingUp = true else votingDown = true
        scope.launch {
            transport.vote(upVote).onFailure { onMessage("投票失败：${it.message}") }
            votingUp = false
            votingDown = false
        }
    }

    // 队列派生值：主页面「下一首」与歌单 Sheet 共用
    val queueArr = pb?.optJSONArray("queue")
    val curIdx = pb?.optInt("currentIndex", -1) ?: -1
    val nextTrack = queueArr?.optJSONObject(curIdx + 1)

// ---- 房间跟随：把房间歌单完整镜像到本地播放器（播放栏可见全队列），
//      引擎自然续播下一首；服务端权威状态每轮轮询校正，本地先行一格不回拉 ----
    /** 本地当前曲目的 stableKey（与房间 key 对齐）。 */
    fun localKeyOf(song: com.wxjxpp.neiro.core.model.Song?): String? = when (val loc = song?.location) {
        is MediaLocation.Remote -> "${loc.sourceId}:${loc.songId}"
        is MediaLocation.Local ->
            if (loc.uri.startsWith("http")) "url:${LitTogetherTransport.urlHash(loc.uri)}" else null
        else -> null
    }

    /** 房间曲目 JSON → Song（withPayload 时才携带取流载荷，队列副本不带）。
     *  注意：房间里的 sourceId 是裸平台名（wy/kg/tx…），本机注册表只有外置源
     *  （wy-lx 等），必须映射过去，否则歌词定位器与取流都找不到音源。 */
    fun songFromTrackJson(t: JSONObject, withPayload: Boolean): Song {
        val rawSource = t.optString("sourceId")
        val mapped = if (rawSource.endsWith("-lx")) rawSource else "${rawSource}-lx"
        return when (rawSource) {
            "url" -> Song(
                id = "lit-${t.optString("stableKey")}",
                title = t.optString("title"),
                artists = listOf(Artist(id = "lit-artist", name = t.optString("artist").ifEmpty { "房间点歌" })),
                durationMs = t.optLong("durationMs"),
                coverUri = t.optString("cover").takeIf { it.isNotEmpty() },
                location = MediaLocation.Local(uri = t.optString("url"), filePath = null),
            )
            else -> Song(
                id = "lit-${t.optString("stableKey")}",
                title = t.optString("title"),
                artists = listOf(Artist(id = "lit-artist", name = t.optString("artist"))),
                album = t.optString("album").takeIf { it.isNotEmpty() }?.let { Album(id = "lit-album", title = it) },
                durationMs = t.optLong("durationMs"),
                coverUri = t.optString("cover").takeIf { it.isNotEmpty() },
                location = MediaLocation.Remote(
                    sourceId = mapped,
                    songId = t.optString("songId"),
                    payload = t.optString("payload").takeIf { withPayload && it.isNotEmpty() },
                ),
            )
        }
    }

    /** 听众的进度/播放状态校正。 */
    fun syncProgressAndPlayState(track: org.json.JSONObject) {
        val stateJson = transport.roomStateJson.value ?: return
        val expected = stateJson.optLong("expectedPositionMs", -1L)
        if (expected >= 0) {
            val drift = kotlin.math.abs(player.state.value.positionMs - expected)
            // 放宽到 2.5s 且不回拉超过 30s 的进度，避免把自然播完的尾部硬拽回去造成循环感
            if (drift > LitTogetherTransport.DRIFT_TOLERANCE_MS && drift < 30_000L) {
                player.seekTo(expected)
            }
        }
        val shouldPlay = stateJson.optJSONObject("playback")?.optBoolean("playing", false) ?: false
        val st = player.state.value
        // 曲目已播完但房间还标记在播：不要强行 resume（等 TRACK_END 推进），否则就是原地循环
        val endedLocally = st.durationMs > 0 && st.positionMs >= st.durationMs - 300
        if (shouldPlay != st.isPlaying && !st.isBuffering && !endedLocally) {
            if (shouldPlay) player.resume() else player.pause()
        }
    }

    LaunchedEffect(Unit) {
        transport.roomStateJson.collect { stateJson ->
            val pb = stateJson?.optJSONObject("playback") ?: return@collect
            val track = pb.optJSONObject("track") ?: return@collect
            val queueArr = pb.optJSONArray("queue")
            val roomIdx = pb.optInt("currentIndex", -1)
            // 完整队列镜像：当前曲用带 payload 的 track，其余用队列副本
            val songs = buildList {
                val n = queueArr?.length() ?: 0
                for (i in 0 until n) {
                    val item = if (i == roomIdx) track else queueArr?.optJSONObject(i) ?: continue
                    add(songFromTrackJson(item, withPayload = i == roomIdx))
                }
            }
            if (songs.isEmpty() || roomIdx !in songs.indices) return@collect
            val keys = songs.map { localKeyOf(it) }
            // 房间模式下强制禁用单曲循环：否则引擎播完原地重播，永远等不到切歌
            // （每轮检查：用户手动开单曲循环后 3s 内也会被纠正回房间语义）
            while (player.state.value.repeatMode == com.wxjxpp.neiro.core.model.RepeatMode.One) {
                player.cycleRepeatMode()
            }
            val localKey = localKeyOf(player.state.value.current)
            val localIdx = keys.indexOf(localKey)
            when {
                localIdx == roomIdx -> {
                    // 已同步：仅校正进度/播放态（听众）
                    if (!transport.isControllerInRoom) syncProgressAndPlayState(track)
                }
                // 本地引擎已自然前进到下一首（快于服务端推进）：等待房间追平，不回拉
                localIdx == (roomIdx + 1).mod(songs.size) -> Unit
                // 其余情况以服务端为准（房主点播/投票切歌/首次进房/加歌后重排）
                else -> {
                    transport.markRemoteSwitch()
                    player.setQueue(songs, roomIdx, autoPlay = true)
                }
            }
        }
    }

    // ---- 播完检测：任一成员本机播完即上报，服务端闸门判定后推进下一首 ----
    LaunchedEffect(Unit) {
        var lastEndedKey = ""
        while (true) {
            kotlinx.coroutines.delay(1000)
            if (!transport.isInRoom()) continue
            val room = transport.roomStateJson.value ?: continue
            val pb = room.optJSONObject("playback") ?: continue
            if (!pb.optBoolean("playing", false)) continue
            val track = transport.currentTrackJson.value ?: continue
            val dur = track.optLong("durationMs", 0L)
            if (dur <= 0) continue
            val st = player.state.value
            val localDur = if (st.durationMs > 0) st.durationMs else dur
            val key = track.optString("stableKey")
            val nearEnd = st.positionMs >= localDur - 400 ||
                (room.optLong("expectedPositionMs", 0L) + 400 >= dur)
            if (nearEnd && lastEndedKey != key) {
                lastEndedKey = key
                transport.reportTrackEnd()
            }
            if (key != lastEndedKey) lastEndedKey = ""
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- 顶部栏：房名+数字ID小字+在线人数（点击看全员）| 复制 | 汉堡菜单 ----
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).clickable { showMembers = true }.padding(vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            r.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$online/${r.members.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        "房间ID ${formatRoomId(r.id)}" +
                            when (connection) {
                                TogetherConnectionState.Connected -> ""
                                TogetherConnectionState.Reconnecting -> " · 重连中…"
                                TogetherConnectionState.Connecting -> " · 连接中…"
                                else -> " · 已断开"
                            } + if (state?.optBoolean("controllerOnline") == false) " · 房主离线" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    clipboard.setText(
                        AnnotatedString(
                            "我在Neiro听歌，复制消息和我一起听\n${formatRoomId(r.id)}|${transport.lastJoinSecret}"
                        )
                    )
                    onMessage("已复制邀请消息，发给朋友粘贴即可加入")
                }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "复制邀请消息") }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "菜单")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (isHost) "关闭房间" else "退出房间") },
                            onClick = {
                                menuOpen = false
                                if (isHost) confirmCloseRoom = true else scope.launch { transport.leaveRoomAndClear() }
                            },
                        )
                    }
                }
            }
        }

// ---- 当前曲目 + 投票进度条 + 赞/踩（中段大块）----
        item {
            CurrentTrackCard(
                transport = transport,
                player = player,
                onMessage = onMessage,
                curTrackJson = curTrack,
                up = up, down = down, online = online, threshold = threshold,
                votedUp = votedUp, votedDown = votedDown,
                votingUp = votingUp, votingDown = votingDown,
                castVote = ::castVote,
            )
        }
        // ---- 搜索点歌：与发现页一致的圆角搜索条 + 平台标签 + 专辑图结果 ----
        item {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("歌名 / 歌手 / 专辑 / 标签") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        when {
                            searching -> androidx.compose.material3.LoadingIndicator(
                                modifier = Modifier.height(18.dp).width(18.dp),
                            )
                            keyword.isNotEmpty() -> IconButton(onClick = {
                                keyword = ""; results = emptyList(); searched = false
                            }) { Icon(Icons.Rounded.Close, contentDescription = "清空") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { runSearch() }),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // 平台标签分类条：官方连通按钮组（与全局聚合搜索一致），横向滚动防溢出
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                ConnectedChoiceGroup(
                    options = search.platforms.map { it.displayName },
                    selectedIndex = search.platforms.indexOfFirst { it.id == platformId }.coerceAtLeast(0),
                    onSelect = { index ->
                        platformId = search.platforms[index].id
                        runSearch()
                    },
                )
            }
        }
        // 搜索结果：必须含专辑图；点添加不关闭结果（无效源可多试几个）
        if (searched || searching) {
            item {
                Box(Modifier.height(240.dp)) {
                    when {
                        searching -> androidx.compose.material3.LoadingIndicator(Modifier.align(Alignment.Center))
                        results.isEmpty() -> Text(
                            "没搜到，换个关键词或标签试试",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(results.size, key = { results[it].id }) { i ->
                                val s = results[i]
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        addingKey = s.id
                                        scope.launch {
                                            // 先用本机音源解析直链：成功则发 URL 曲目（全房间免脚本直接播）
                                            val url = runCatching { resolveUrl(s) }.getOrNull()
                                            val res = if (!url.isNullOrEmpty()) {
                                                transport.addSongByUrl(url, s.title, s.artistName, s.coverUri.orEmpty())
                                            } else {
                                                transport.addSongFromPlatform(s)
                                            }
                                            addingKey = null
                                            onMessage(
                                                res.fold(
                                                    { if (url.isNullOrEmpty()) "已加入列表（该源无效时听众需自备音源）" else "已加入列表" },
                                                    { it.message ?: "添加失败" },
                                                ),
                                            )
                                        }
                                    }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SongCover(
                                        coverUri = s.coverUri,
                                        seedColor = s.coverSeedColor,
                                        size = 46.dp,
                                        radius = 8.dp,
                                    )
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(
                                            s.artistName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                    if (addingKey == s.id) {
                                        androidx.compose.material3.LoadingIndicator(
                                            modifier = Modifier.height(20.dp).width(20.dp),
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.AddLink, contentDescription = "点这首",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

// ---- 队列：主页只显示「下一首」小字（点击展开歌单 Sheet）----
        if (nextTrack != null) {
            item {
                Text(
                    "下一首 ${nextTrack.optString("title")} · 点此查看歌单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().clickable { showQueue = true },
                )
            }
        } else if ((queueArr?.length() ?: 0) > 0) {
            item {
                Text(
                    "已播到最后 · 点此查看歌单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { showQueue = true },
                )
            }
        }

        // ---- 弹幕 ----
        item {
            ChatSection(transport)
        }
        // 底部留白：避免内容被播放栏/导航遮挡
        item { Spacer(Modifier.height(96.dp)) }
    }

    // ---- 全员列表 Sheet：在线在前，离线灰字+断线图标，房主可移除 ----
    if (showMembers) {
        ModalBottomSheet(onDismissRequest = { showMembers = false }) {
            Text(
                "成员（$online/${r.members.size} 在线）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            val sorted = r.members.sortedByDescending { it.online }
            LazyColumn(Modifier.padding(horizontal = 20.dp)) {
                items(sorted.size) { i ->
                    val m = sorted[i]
                    MemberRow(
                        transport, m.id, m.name,
                        isHost = m.isHost, online = m.online,
                        onKickRequest = { confirmKickTarget = m },
                    )
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
        }
    }
    // ---- 歌单 Sheet ----
    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            Text(
                "歌单（${queueArr?.length() ?: 0}）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.padding(horizontal = 20.dp)) {
                val n = queueArr?.length() ?: 0
                items(n) { i ->
                    val trackItem = queueArr?.optJSONObject(i) ?: return@items
                    QueueItem(
                        transport,
                        trackItem,
                        isCurrent = i == curIdx,
                        isHost = isHost,
                        onMessage = onMessage,
                    )
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
        }
    }
    // ---- 踢人二次确认 → 确认后才执行并显示加载 ----
    confirmKickTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!kicking) confirmKickTarget = null },
            title = { Text("移出成员") },
            text = { Text("确定将「${target.name}」移出房间吗？对方会收到提示。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    kicking = true
                    scope.launch {
                        transport.kick(target.id).fold(
                            onSuccess = { onMessage("已移出 ${target.name}") },
                            onFailure = { onMessage("移出失败：${it.message}") },
                        )
                        kicking = false
                        confirmKickTarget = null
                    }
                }) { Text("确定移出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { confirmKickTarget = null }, enabled = !kicking,
                ) { Text("取消") }
            },
        )
    }
    // ---- 关闭房间二次确认（仅房主可见此项）----
    if (confirmCloseRoom) {
        AlertDialog(
            onDismissRequest = { if (!closing) confirmCloseRoom = false },
            title = { Text("关闭房间") },
            text = { Text("关闭后所有成员都会断开，房间无法恢复。确定关闭吗？") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    closing = true
                    scope.launch {
                        runCatching { transport.leaveRoomAndClear() }
                        closing = false
                        confirmCloseRoom = false
                    }
                }) { Text("确定关闭", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { confirmCloseRoom = false }, enabled = !closing,
                ) { Text("取消") }
            },
        )
    }
    // ---- 破坏性操作加载层（踢人/关房确认成功后出现）----
    if (kicking || closing) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.LoadingIndicator()
                Spacer(Modifier.height(12.dp))
                Text(if (closing) "正在关闭房间…" else "正在处理…")
            }
        }
    }
}
@Composable
private fun QueueItem(
    transport: LitTogetherTransport,
    t: JSONObject?,
    isCurrent: Boolean,
    isHost: Boolean,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (t == null) return
    Card(Modifier.fillMaxWidth().clickable {
        if (isHost) {
            // 房主：点歌单任意曲目 → 服务端 PLAY_INDEX 开播，全房跟随
            scope.launch {
                transport.playIndex(transport.roomStateJson.value
                    ?.optJSONObject("playback")?.optJSONArray("queue")
                    ?.let { q -> (0 until q.length()).firstOrNull { q.optJSONObject(it)?.optString("stableKey") == t.optString("stableKey") } }
                    ?: -1,
                ).fold(
                    onSuccess = { },
                    onFailure = { onMessage("播放失败：${it.message}") },
                )
            }
        }
    }) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.PlayArrow, contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    (if (t.optBoolean("invalid", false)) "⚠ " else "") + t.optString("title"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    listOf(
                        t.optString("artist"),
                        t.optString("addedBy")?.let { "$it 点播" },
                    ).filter { !it.isNullOrEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isHost) {
                IconButton(onClick = {
                    scope.launch {
                        transport.removeSong(t.optString("stableKey"))
                            .onFailure { onMessage("移除失败：${it.message}") }
                    }
                }) { Icon(Icons.Rounded.Close, contentDescription = "移除") }
            }
        }
    }
}

@Composable
private fun ChatSection(transport: LitTogetherTransport) {
    val scope = rememberCoroutineScope()
    val state by transport.roomStateJson.collectAsState()
    var input by remember { mutableStateOf("") }
    val chat = state?.optJSONArray("chat")
    Column {
        Text("弹幕", style = MaterialTheme.typography.titleSmall)
        if (chat == null || chat.length() == 0) {
            Text("还没有消息，说点什么吧", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        chat?.let { arr ->
            val from = maxOf(0, arr.length() - 20)
            for (i in from until arr.length()) {
                val c = arr.getJSONObject(i)
                Text(
                    "${c.optString("from")}：${c.optString("text")}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("发条弹幕…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val text = input.trim()
                if (text.isEmpty()) return@IconButton
                scope.launch {
                    transport.chat(text).fold(
                        onSuccess = { input = "" },
                        onFailure = { },
                    )
                }
            }) { Icon(Icons.Rounded.Send, contentDescription = "发送") }
        }
    }
}
@Composable
private fun CurrentTrackCard(
    transport: LitTogetherTransport,
    player: PlayerController,
    onMessage: (String) -> Unit,
    curTrackJson: JSONObject?,
    up: Int, down: Int, online: Int, threshold: Int,
    votedUp: Boolean, votedDown: Boolean,
    votingUp: Boolean, votingDown: Boolean,
    castVote: (Boolean) -> Unit,
) {
    val downRatio = down.toFloat() / online
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 大块专辑图（必须显示）
            SongCover(
                coverUri = curTrackJson?.optString("cover").takeIf { !it.isNullOrEmpty() },
                seedColor = 0xFF4F5B92,
                size = 96.dp,
                radius = 12.dp,
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("正在播放", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    curTrackJson?.optString("title").takeIf { !it.isNullOrEmpty() }
                        ?: "待机中 · 等待第一首歌",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                Text(
                    buildString {
                        append(curTrackJson?.optString("artist").orEmpty().ifEmpty { "未知艺术家" })
                        curTrackJson?.optString("addedBy")?.takeIf { it.isNotEmpty() }?.let { append(" · 由 $it 点播") }
                        if (curTrackJson?.optBoolean("invalid", false) == true) append(" · ⚠ 无效源")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        // 投票进度条：红色为踩票占比，达阈值即自动切歌
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (downRatio > 0f) {
                    Box(
                        Modifier.fillMaxWidth(downRatio.coerceIn(0f, 1f)).height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }
            Text(
                "  $up 赞 · $down 踩 / ${online}人 · 达 ${threshold}% 自动切",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 房主不参与投票（民主切歌只看群友），仅群友显示投票按钮 + 按钮旁小加载动画
        if (!transport.isControllerInRoom && curTrackJson != null) {
            Row(Modifier.padding(start = 6.dp)) {
                IconButton(onClick = { castVote(true) }, enabled = !votingUp && !votingDown) {
                    Icon(Icons.Rounded.ThumbUpAlt, contentDescription = "赞",
                        tint = if (votedUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (votingUp) androidx.compose.material3.LoadingIndicator(Modifier.height(18.dp).width(18.dp))
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { castVote(false) }, enabled = !votingUp && !votingDown)
{
                    Icon(Icons.Rounded.ThumbDownAlt, contentDescription = "踩",
                        tint = if (votedDown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (votingDown) androidx.compose.material3.LoadingIndicator(Modifier.height(18.dp).width(18.dp))
                Text(
                    when {
                        votedUp -> " 已投赞"
                        votedDown -> " 已投踩"
                        else -> ""
                    },
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
@Composable
private fun MemberRow(
    transport: LitTogetherTransport,
    memberId: String,
    name: String,
    isHost: Boolean,
    online: Boolean,
    onKickRequest: () -> Unit,
) {
    val self = transport.selfMemberId
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when {
                isHost -> "👑 $name"
                memberId == self -> "$name（我）"
                else -> name
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (online) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!online) {
            Icon(
                Icons.Rounded.WifiOff, contentDescription = "已断线",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(16.dp).width(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        val canKick = transport.isControllerInRoom && !isHost && memberId != self
        if (canKick) {
            IconButton(onClick = onKickRequest) {
                Icon(Icons.Rounded.PersonRemove, contentDescription = "移出房间")
            }
        }
    }
}
