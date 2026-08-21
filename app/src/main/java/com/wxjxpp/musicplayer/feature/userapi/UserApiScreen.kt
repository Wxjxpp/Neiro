package com.wxjxpp.musicplayer.feature.userapi

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.wxjxpp.musicplayer.core.userapi.UserApiInfo
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/**
 * 自定义音源管理页。
 *
 * 两种导入方式：本地 `.js` 文件，或直接填脚本 URL（宿主下载后再交给 QuickJS）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserApiScreen(
    apis: List<UserApiInfo>,
    onImportScript: (String) -> Unit,
    onImportUrl: (String) -> Unit,
    onActivate: (String) -> Unit,
    onRemove: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    val context = LocalContext.current
    var showUrlDialog by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<UserApiInfo?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()?.let(onImportScript)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Column(modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm)) {
            Text("自定义音源", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "支持 LX 格式的 .js 脚本，在 QuickJS 沙箱内执行，网络请求由宿主代发",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = dimens.spaceSm),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            ) {
                FilledTonalButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                    Text("本地文件", modifier = Modifier.padding(start = dimens.spaceXs))
                }
                FilledTonalButton(onClick = { showUrlDialog = true }) {
                    Icon(Icons.Filled.Link, contentDescription = null)
                    Text("从 URL", modifier = Modifier.padding(start = dimens.spaceXs))
                }
            }
        }

        if (apis.isEmpty()) {
            Text(
                text = "尚未导入任何音源脚本",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(dimens.spaceLg),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(apis, key = { it.id }) { api ->
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Extension, contentDescription = null) },
                        headlineContent = { Text(api.name) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    api.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                                    api.author.takeIf { it.isNotBlank() },
                                    api.description.takeIf { it.isNotBlank() },
                                ).joinToString(" · ").ifBlank { api.id }
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onActivate(api.id) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "启用")
                                }
                                IconButton(onClick = { removeTarget = api }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("从 URL 导入") },
            text = {
                Column {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        placeholder = { Text("https://example.com/source.js") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Text(
                        text = "仅从可信来源导入脚本：脚本可代表你发起网络请求。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppTheme.dimens.spaceSm),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (url.isNotBlank()) onImportUrl(url.trim())
                    showUrlDialog = false
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("取消") }
            },
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("删除音源") },
            text = { Text("确定删除「${target.name}」？") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(target.id)
                    removeTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("取消") } },
        )
    }
}