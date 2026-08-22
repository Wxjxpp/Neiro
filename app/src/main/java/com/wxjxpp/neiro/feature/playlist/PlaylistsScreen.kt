package com.wxjxpp.neiro.feature.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.wxjxpp.neiro.core.model.Playlist
import com.wxjxpp.neiro.core.model.Song
import com.wxjxpp.neiro.ui.components.SongCover
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 歌单页。
 *
 * 点击歌单进入歌曲列表（同一页内切换，不新增路由）；
 * 删除有二次确认；重命名走独立按钮，不再和"打开"冲突。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    songsById: Map<String, Song>,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onPlay: (Playlist) -> Unit,
    onPlaySongInPlaylist: (Playlist, Int) -> Unit,
    onRemoveSongs: (String, List<String>) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var openedId by remember { mutableStateOf<String?>(null) }

    val opened = playlists.firstOrNull { it.id == openedId }

    if (opened != null) {
        PlaylistDetail(
            playlist = opened,
            songsById = songsById,
            onBack = { openedId = null },
            onPlayAll = { onPlay(opened) },
            onPlayAt = { index -> onPlaySongInPlaylist(opened, index) },
            onRemoveSong = { songId -> onRemoveSongs(opened.id, listOf(songId)) },
            contentPadding = contentPadding,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "共 ${playlists.size} 个歌单",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("新建", modifier = Modifier.padding(start = dimens.spaceXs))
            }
        }

        if (playlists.isEmpty()) {
            Text(
                text = "还没有歌单，点右上角新建",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(dimens.spaceLg),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { playlist ->
                    ListItem(
                        modifier = Modifier.clickable { openedId = playlist.id },
                        leadingContent = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.songIds.size} 首") },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                                IconButton(onClick = { onPlay(playlist) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放歌单")
                                }
                                IconButton(onClick = { renameTarget = playlist }) {
                                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "重命名")
                                }
                                IconButton(onClick = { deleteTarget = playlist }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除歌单")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        TextInputDialog(
            title = "新建歌单",
            initialValue = "",
            confirmLabel = "创建",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                if (name.isNotBlank()) onCreate(name.trim())
                showCreateDialog = false
            },
        )
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = "重命名歌单",
            initialValue = target.name,
            confirmLabel = "保存",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                if (name.isNotBlank()) onRename(target.id, name.trim())
                renameTarget = null
            },
        )
    }

    // 删除是不可逆操作，必须二次确认
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除歌单") },
            text = { Text("确定删除「${target.name}」？歌单内的 ${target.songIds.size} 首歌曲不会从曲库移除。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

/** 歌单内的歌曲列表。 */
@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    songsById: Map<String, Song>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRemoveSong: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    val songs = playlist.songIds.mapNotNull { songsById[it] }

    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spaceSm, vertical = dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回歌单列表")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${songs.size} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPlayAll) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "播放全部")
            }
        }

        if (songs.isEmpty()) {
            Text(
                text = "歌单是空的，去歌曲页长按多选后加入",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(dimens.spaceLg),
            )
            return
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                ListItem(
                    modifier = Modifier.clickable { onPlayAt(index) },
                    leadingContent = {
                        SongCover(
                            song = song,
                            size = dimens.listCoverSize,
                            radius = dimens.playerBarCoverRadius,
                        )
                    },
                    headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            "${song.artistName} · ${song.albumTitle}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onRemoveSong(song.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "从歌单移除")
                        }
                    },
                )
            }
        }
    }
}

/** 选择目标歌单的对话框：多选后"加入歌单"用。 */
@Composable
fun PickPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreateNew: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        TextInputDialog(
            title = "新建歌单并加入",
            initialValue = "",
            confirmLabel = "创建",
            onDismiss = { creating = false },
            onConfirm = { name ->
                if (name.isNotBlank()) onCreateNew(name.trim())
                creating = false
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入歌单") },
        text = {
            if (playlists.isEmpty()) {
                Text("还没有歌单，可以直接新建一个")
            } else {
                LazyColumn {
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            modifier = Modifier.clickable { onPick(playlist.id) },
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.songIds.size} 首") },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("新建歌单") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text("歌单名称") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}