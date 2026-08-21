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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
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
import com.wxjxpp.musicplayer.core.userapi.UserApiStatus
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/**
 * 自定义音源管理页。
 *
 * - 本地 `.js` 文件 / URL 两种导入方式
 * - 显示每个脚本初始化后上报的能力（支持的平台与音质）
 * - 启用失败时给出可读原因
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserApiScreen(
    apis: List<UserApiInfo>,
    engineStatus: UserApiStatus?,
    onImportScript: (String) -> Unit,
    onImportUrl: (String) -> Unit,
    onActivate: (String) -> Unit,
    onDeactivate: () -> Unit,
    onUpdate: (String) -> Unit,
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
                text = "支持 LX 格式的 .js 脚本，在 QuickJS 沙箱内执行，网络请求由宿主代发。" +
                    "在线搜索无需音源；音源脚本负责解析各平台的播放地址。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 引擎状态
            when (val status = engineStatus) {
                is UserApiStatus.Initializing -> StatusText("正在初始化「${status.id}」…", MaterialTheme.colorScheme.onSurfaceVariant)
                is UserApiStatus.Ready -> StatusText(
                    "已启用：${status.info.name}" +
                        (status.info.platforms.takeIf { it.isNotEmpty() }?.let { "（${it.joinToString("、")}）" } ?: ""),
                    MaterialTheme.colorScheme.primary,
                )
                is UserApiStatus.Failed -> StatusText("启用失败：${status.message}", MaterialTheme.colorScheme.error)
                else -> Unit
            }
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
                if (engineStatus is UserApiStatus.Ready) {
                    FilledTonalButton(onClick = onDeactivate) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text("停用", modifier = Modifier.padding(start = dimens.spaceXs))
                    }
                }
            }
        }

        if (apis.isEmpty()) {
            Text(
                text = "尚未导入任何音源脚本。\n在线搜索与歌词不需要音源；导入音源后才能播放在线歌曲。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(dimens.spaceLg),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(apis, key = { it.id }) { api ->
                    val isActive = (engineStatus as? UserApiStatus.Ready)?.info?.id == api.id
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Extension, contentDescription = null) },
                        headlineContent = { Text(api.name + if (isActive) "（使用中）" else "") },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    api.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                                    api.author.takeIf { it.isNotBlank() },
                                    api.description.takeIf { it.isNotBlank() },
                                    api.platforms.takeIf { it.isNotEmpty() }
                                        ?.let { "支持：${it.joinToString("、")}" },
                                ).joinToString(" · ").ifBlank { api.id }
                            )
                        },
                        trailingContent = {
                            Row {
                                if (isActive) {
                                    IconButton(onClick = onDeactivate) {
                                        Icon(Icons.Filled.Stop, contentDescription = "停用")
                                    }
                                } else {
                                    IconButton(onClick = { onActivate(api.id) }) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "启用")
                                    }
                                }
                                if (api.sourceUrl != null) {
                                    IconButton(onClick = { onUpdate(api.id) }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "更新")
                                    }
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

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color) {
    val dimens = AppTheme.dimens
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = dimens.spaceXs),
    )
}