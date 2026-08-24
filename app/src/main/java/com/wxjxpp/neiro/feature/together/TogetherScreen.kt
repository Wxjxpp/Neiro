package com.wxjxpp.neiro.feature.together

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * 一起听页面。
 *
 * 未入房：填服务端地址 / 昵称 → 创建房间 或 加入房间（房间号+邀请密钥）。
 * 已入房：显示成员、当前曲目、连接状态；房主可直接控制，听众发请求。
 * 同步逻辑：监听 [LitTogetherTransport.currentTrackJson]，与本地播放不一致时自动切歌；
 * 播放/暂停/进度按服务端权威快照校正（>800ms 才 seek）。
 */
@Composable
fun TogetherScreen(
    transport: LitTogetherTransport,
    player: PlayerController,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val connection by transport.connectionState
    val room by transport.room
    val inRoom = room != null && connection != TogetherConnectionState.Disconnected

    if (inRoom) {
        RoomView(transport, player, onMessage, modifier)
    } else {
        LobbyView(transport, onMessage, modifier)
    }
}

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
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("一起听", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "和好友实时同步听歌。需要自部署 Neiro-LIT 服务端（支持 Cloudflare / Vercel）。",
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
                    val r = transport.createRoom(url, nick.trim())
                    busy = false
                    onMessage(r.fold({ "房间已创建：$it（邀请密钥见下方）" }, { "创建失败：${it.message}" }))
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
                    val r = transport.joinRoom(url, roomId.trim(), nick.trim(), secret.trim())
                    busy = false
                    onMessage(r.fold({ "已加入房间 $it" }, { "加入失败：${it.message}" }))
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("加入房间") }
    }
}

@Composable
private fun RoomView(
    transport: LitTogetherTransport,
    player: PlayerController,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val connection by transport.connectionState
    val room by transport.room
    val state by transport.roomStateJson
    val clipboard = LocalClipboardManager.current
    val r = room ?: return

    // ---- 播放同步核心 ----
    LaunchedEffect(Unit) {
        transport.currentTrackJson.collect { track ->
            val local = player.state.value.current
            if (track == null) {
                player.pause()
                return@collect
            }
            val sourceId = track.optString("sourceId")
            val songId = track.optString("songId")
            val wantKey = "$sourceId:$songId"
            val localKey = (local?.location as? MediaLocation.Remote)?.let { "${it.sourceId}:${it.songId}" }
            if (localKey != wantKey) {
                // 切歌：用 payload 还原完整在线歌曲交给播放引擎
                val song = Song(
                    id = "lit-$wantKey",
                    title = track.optString("title"),
                    artists = listOf(Artist(id = "lit-artist", name = track.optString("artist"))),
                    album = track.optString("album").takeIf { it.isNotEmpty() }?.let {
                        Album(id = "lit-album", title = it)
                    },
                    durationMs = track.optLong("durationMs"),
                    coverUri = track.optString("cover").takeIf { it.isNotEmpty() },
                    location = MediaLocation.Remote(
                        sourceId = sourceId,
                        songId = songId,
                        payload = track.optString("payload").takeIf { it.isNotEmpty() },
                    ),
                )
                player.setQueue(listOf(song), 0, autoPlay = true)
            }
            // 进度校正：偏差 >800ms 才 seek，避免与本地自然播放打架
            val expected = transport.roomStateJson.value?.optLong("expectedPositionMs", -1L) ?: -1L
            if (expected >= 0) {
                val drift = kotlin.math.abs(player.state.value.positionMs - expected)
                if (drift > LitTogetherTransport.DRIFT_TOLERANCE_MS) player.seekTo(expected)
            }
            val shouldPlay = transport.roomStateJson.value
                ?.optJSONObject("playback")?.optBoolean("playing", false) ?: false
            if (shouldPlay != player.state.value.isPlaying) {
                if (shouldPlay) player.resume() else player.pause()
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
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
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("当前曲目", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val t = transport.currentTrackJson
                    Text(
                        t?.optString("title").takeIf { !it.isNullOrEmpty() } ?: "暂无",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(t?.optString("artist").orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("成员 (${r.members.size})", style = MaterialTheme.typography.titleSmall) }
        items(r.members, key = { it.id }) { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (m.isHost) "👑 ${m.name}" else m.name, modifier = Modifier.weight(1f))
                Text(if (m.isHost) "房主" else "听众",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
