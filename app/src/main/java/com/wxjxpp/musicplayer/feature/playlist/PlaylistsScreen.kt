package com.wxjxpp.musicplayer.feature.playlist

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
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
import com.wxjxpp.musicplayer.core.model.Playlist
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/**
 * 歌单页。
 *
 * 顶部无标题栏（顶部栏由壳层统一控制），只保留列表与新建入口。
 */
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onPlay: (Playlist) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }

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
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建歌单")
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
                        modifier = Modifier.clickable { renameTarget = playlist },
                        leadingContent = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.songIds.size} 首") },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                                IconButton(onClick = { onPlay(playlist) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放歌单")
                                }
                                IconButton(onClick = { onDelete(playlist.id) }) {
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
        confirmButton = {
            TextButton(onClick = { creating = true }) { Text("新建歌单") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
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