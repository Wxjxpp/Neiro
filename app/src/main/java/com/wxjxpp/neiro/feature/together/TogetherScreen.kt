package com.wxjxpp.neiro.feature.together

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ThumbDownAlt
import androidx.compose.material.icons.rounded.ThumbUpAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    var url by remember(savedUrl) { mutableStateOf(savedUrl.ifEmpty { "https://wxjxpp.de5.net" }) }
    var nick by remember(savedNick) { mutableStateOf(savedNick) }
    var roomId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding() // 键盘弹起时整体上移，昵称框不再被输入法遮挡
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("一起听", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "和好友实时同步听歌。群友可以加歌（URL 直链）、投票切歌、发弹幕；房主拥有全部控制权。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("服务端地址") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = nick, onValueChange = { nick = it },
            label = { Text("昵称（1-24 位中文/字母/数字）") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                if (nick.isBlank()) { onMessage("请先填写昵称"); return@Button }
                busy = true
                scope.launch {
                    val r = transport.createRoomAt(url, nick.trim())
                    busy = false
                    onMessage(r.fold({ "房间已创建：$it" }, { "创建失败：${it.message}" }))
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("创建房间（我是房主）")
        }
        Text("—— 或加入他人房间 ——", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        OutlinedTextField(
            value = roomId, onValueChange = { roomId = it.uppercase() },
            label = { Text("房间号（6 位）") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = secret, onValueChange = { secret = it },
            label = { Text("邀请密钥（首次加入必填）") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                if (nick.isBlank() || roomId.isBlank() || secret.isBlank()) {
                    onMessage("昵称、房间号、邀请密钥都要填"); return@OutlinedButton
                }
                busy = true
                scope.launch {
                    val r = transport.joinRoomAt(url, roomId.trim(), nick.trim(), secret.trim())
                    busy = false
                    onMessage(r.fold({ "已加入房间 $it" }, { "加入失败：${it.message}" }))
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("加入房间") }
        Spacer(Modifier.height(24.dp))
    }
}

// ================= 房间内 =================

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

    // ---- 听众跟随服务端（房主由 AppContainer 桥上报，这里跳过防回环） ----
    LaunchedEffect(Unit) {
        transport.currentTrackJson.collect { track ->
            if (transport.isControllerInRoom) return@collect
            val local = player.state.value.current
            if (track == null) {
                // 待机态：房间没有曲目，不强制暂停本地播放之外的任何东西
                return@collect
            }
            val key = track.optString("stableKey")
            val localKey = when (val loc = local?.location) {
                is MediaLocation.Remote -> "${loc.sourceId}:${loc.songId}"
                is MediaLocation.Local ->
                    if (loc.uri.startsWith("http")) "url:${LitTogetherTransport.urlHash(loc.uri)}" else null
                else -> null
            }
            if (localKey != key) {
                // 切歌：URL 直链直接播；平台曲目用 payload 还原完整歌曲交给播放引擎
                val song = when (track.optString("sourceId")) {
                    "url" -> Song(
                        id = "lit-$key",
                        title = track.optString("title"),
                        artists = listOf(Artist(id = "lit-artist", name = track.optString("artist").ifEmpty { "房间点歌" })),
                        durationMs = track.optLong("durationMs"),
                        coverUri = track.optString("cover").takeIf { it.isNotEmpty() },
                        location = MediaLocation.Local(uri = track.optString("url"), filePath = null),
                    )
                    else -> Song(
                        id = "lit-$key",
                        title = track.optString("title"),
                        artists = listOf(Artist(id = "lit-artist", name = track.optString("artist"))),
                        album = track.optString("album").takeIf { it.isNotEmpty() }?.let {
                            Album(id = "lit-album", title = it)
                        },
                        durationMs = track.optLong("durationMs"),
                        coverUri = track.optString("cover").takeIf { it.isNotEmpty() },
                        location = MediaLocation.Remote(
                            sourceId = track.optString("sourceId"),
                            songId = track.optString("songId"),
                            payload = track.optString("payload").takeIf { it.isNotEmpty() },
                        ),
                    )
                }
                player.setQueue(listOf(song), 0, autoPlay = true)
            }
            // 进度校正：偏差 >800ms 才 seek，避免与本地自然播放打架
            val expected = state?.optLong("expectedPositionMs", -1L) ?: -1L
            if (expected >= 0) {
                val drift = kotlin.math.abs(player.state.value.positionMs - expected)
                if (drift > LitTogetherTransport.DRIFT_TOLERANCE_MS) player.seekTo(expected)
            }
            val shouldPlay = state?.optJSONObject("playback")?.optBoolean("playing", false) ?: false
            if (shouldPlay != player.state.value.isPlaying && !player.state.value.isBuffering) {
                if (shouldPlay) player.resume() else player.pause()
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- 标题栏：状态 + 复制邀请 + 离开 ----
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        when (connection) {
                            TogetherConnectionState.Connected -> "已连接"
                            TogetherConnectionState.Reconnecting -> "重连中…"
                            TogetherConnectionState.Connecting -> "连接中…"
                            else -> "已断开"
                        } + if (state?.optBoolean("controllerOnline") == false) " · 房主离线" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    val invite = "${r.id}|${transport.lastJoinSecret}"
                    clipboard.setText(AnnotatedString(invite))
                    onMessage(if (invite.endsWith("|")) "已复制房间号（密钥仅创建者持有）" else "已复制邀请信息，发给朋友即可加入")
                }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "复制邀请") }
                IconButton(onClick = {
                    scope.launch { transport.leaveRoomAndClear() }
                }) { Icon(Icons.Rounded.Logout, contentDescription = "离开房间") }
            }
        }

        // ---- 邀请密钥卡片（修复：toast 说"见下方"但从不展示的问题）----
        item {
            val js = transport.lastJoinSecret
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("邀请密钥", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            js.ifEmpty { "（你不是创建者，无密钥）" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (js.isNotEmpty()) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString("${r.id}|$js"))
                            onMessage("已复制「房间号|密钥」，发给朋友即可加入")
                        }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "复制") }
                    }
                }
            }
        }

        // ---- 当前曲目 + 投票进度条 + 赞/踩 ----
        item {
            CurrentTrackCard(transport, player, onMessage)
        }

        // ---- 加歌：搜索点歌（复用全局聚合搜索）----
        item {
            AddSongRow(
                transport,
                onMessage,
                search = search,
                resolveUrl = resolveUrl,
            )
        }

        // ---- 队列 ----
        val queue = state?.optJSONObject("playback")?.optJSONArray("queue")
        if (queue != null && queue.length() > 0) {
            item { Text("播放列表 (${queue.length()})", style = MaterialTheme.typography.titleSmall) }
            items(queue.length()) { i ->
                QueueItem(
                    transport,
                    queue.getJSONObject(i),
                    isCurrent = i == (state?.optJSONObject("playback")?.optInt("currentIndex", -1) ?: -1),
                    isHost = transport.isControllerInRoom,
                    onMessage = onMessage,
                )
            }
        }

        // ---- 聊天/弹幕 ----
        item {
            ChatSection(transport)
        }

        // ---- 成员（房主可踢人）----
        item { Text("成员 (${r.members.size} · 在线 ${state?.optInt("onlineCount", r.members.size) ?: r.members.size})",
            style = MaterialTheme.typography.titleSmall) }
        items(r.members, key = { it.id }) { m ->
            MemberRow(transport, m.id, m.name, m.isHost, onMessage)
        }
        // 底部留白：避免最后一位成员名被播放栏/导航遮挡
        item { Spacer(Modifier.height(96.dp)) }
    }
}
@Composable
private fun CurrentTrackCard(
    transport: LitTogetherTransport,
    player: PlayerController,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by transport.roomStateJson.collectAsState()
    val t by transport.currentTrackJson.collectAsState()
    val pb = state?.optJSONObject("playback")
    val votes = state?.optJSONObject("voteSummary")
    val online = state?.optInt("onlineCount", 1)?.coerceAtLeast(1) ?: 1
    val up = votes?.optInt("up", 0) ?: 0
    val down = votes?.optInt("down", 0) ?: 0
    val threshold = votes?.optInt("threshold", 50) ?: 50
    val downRatio = down.toFloat() / online
    val myId = transport.selfMemberId
    val votedUp = votes?.optJSONArray("upIds")?.let { a -> (0 until a.length()).any { a.optString(it) == myId } } == true
    val votedDown = votes?.optJSONArray("downIds")?.let { a -> (0 until a.length()).any { a.optString(it) == myId } } == true

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("当前曲目", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                t?.optString("title").takeIf { !it.isNullOrEmpty() } ?: "待机中 · 等待第一首歌",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                buildString {
                    append(t?.optString("artist").orEmpty().ifEmpty { "未知艺术家" })
                    t?.optString("addedBy")?.takeIf { it.isNotEmpty() }?.let { append(" · 由 $it 点播") }
                    if (t?.optBoolean("invalid", false) == true) append(" · ⚠ 无效源")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // 投票进度条：红色为踩票占比，达阈值即自动切歌
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            if (t != null) {
                Spacer(Modifier.height(4.dp))
                // 房主不参与投票（民主切歌只看群友），仅群友显示投票按钮
                if (!transport.isControllerInRoom) {
                    Row {
                        IconButton(onClick = {
                            if (votedUp || votedDown) { onMessage("每人每首歌限投一次"); return@IconButton }
                            scope.launch { transport.vote(true).onFailure { onMessage("投票失败：${it.message}") } }
                        }) {
                            Icon(Icons.Rounded.ThumbUpAlt, contentDescription = "赞",
                                tint = if (votedUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            if (votedUp || votedDown) { onMessage("每人每首歌限投一次"); return@IconButton }
                            scope.launch { transport.vote(false).onFailure { onMessage("投票失败：${it.message}") } }
                        }) {
                            Icon(Icons.Rounded.ThumbDownAlt, contentDescription = "踩",
                                tint = if (votedDown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
    }
}

@Composable
private fun AddSongRow(
    transport: LitTogetherTransport,
    onMessage: (String) -> Unit,
    search: com.wxjxpp.neiro.core.search.OnlineSearchRepository,
    resolveUrl: suspend (com.wxjxpp.neiro.core.model.Song) -> String?,
) {
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showSearch = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("搜索点歌（全员可用）")
        }
        if (adding) CircularProgressIndicator(Modifier.height(18.dp))
    }

    if (showSearch) {
        SongPickDialog(
            search = search,
            onDismiss = { showSearch = false },
            onPick = { song ->
                showSearch = false
                adding = true
                scope.launch {
                    // 先用本机音源解析直链：成功则发 URL 曲目（全房间免脚本直接播）
                    val url = runCatching { resolveUrl(song) }.getOrNull()
                    val r = if (!url.isNullOrEmpty()) {
                        transport.addSongByUrl(url, song.title, song.artistName, song.coverUri.orEmpty())
                    } else {
                        // 解析失败回退：平台曲目模式，听众用各自脚本取流
                        transport.addSongFromPlatform(song)
                    }
                    adding = false
                    onMessage(
                        r.fold(
                            { if (url.isNullOrEmpty()) "已加入列表（听众需自备音源）" else "已加入列表" },
                            { "添加失败：${it.message}" },
                        ),
                    )
                }
            },
        )
    }
}

@Composable
private fun SongPickDialog(
    search: com.wxjxpp.neiro.core.search.OnlineSearchRepository,
    onDismiss: () -> Unit,
    onPick: (com.wxjxpp.neiro.core.model.Song) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.wxjxpp.neiro.core.model.Song>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {},
        title = { Text("搜索点歌") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("歌名 / 歌手") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            if (keyword.isBlank()) return@IconButton
                            searching = true
                            scope.launch {
                                val r = search.search(keyword)
                                results = r.songs
                                searching = false
                                searched = true
                            }
                        }) { Icon(Icons.Rounded.Search, contentDescription = "搜索") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.height(360.dp)) {
                    when {
                        searching -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        results.isEmpty() && searched -> Text(
                            "没搜到，换个关键词试试",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(results.size) { i ->
                                val s = results[i]
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { onPick(s) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(
                                            s.artistName + " · " + s.albumTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                    IconButton(onClick = { onPick(s) }) {
                                        Icon(Icons.Rounded.AddLink, contentDescription = "点这首")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun QueueItem(
    transport: LitTogetherTransport,
    t: JSONObject,
    isCurrent: Boolean,
    isHost: Boolean,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
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
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
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
private fun MemberRow(
    transport: LitTogetherTransport,
    memberId: String,
    name: String,
    isHost: Boolean,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val self = transport.selfMemberId
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (isHost) "👑 $name" else if (memberId == self) "$name（我）" else name,
            modifier = Modifier.weight(1f),
        )
        if (!isHost && memberId != self) {
            IconButton(onClick = {
                scope.launch {
                    transport.kick(memberId).fold(
                        onSuccess = { onMessage("已移除 $name") },
                        onFailure = { onMessage("移除失败：${it.message}") },
                    )
                }
            }) { Icon(Icons.Rounded.PersonRemove, contentDescription = "移出房间") }
        }
    }
}