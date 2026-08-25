package com.wxjxpp.neiro.feature.userapi

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.wxjxpp.neiro.core.userapi.UserApiInfo
import com.wxjxpp.neiro.core.userapi.UserApiStatus
import com.wxjxpp.neiro.ui.theme.AppTheme

/**
 * 自定义音源管理页。
 *
 * - 导入入口合并为一个「添加音源」按钮 → ActionSheet 选择本地文件 / URL（紧凑，不占两颗按钮）
 * - 显示每个脚本初始化后上报的能力（支持的平台与音质）
 * - 启用失败时给出可读原因
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    /** 测试握手：对指定音源发一次真实脚本请求，验证脚本引擎与网络通路。 */
    onTestHandshake: suspend (UserApiInfo) -> String,
    onOpenDrawer: () -> Unit = {},
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    val context = LocalContext.current
    var showUrlDialog by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<UserApiInfo?>(null) }
    // 握手测试状态：apiId → 结果文本（进行中显示"…"）
    var testingId by remember { mutableStateOf<String?>(null) }
    var testResultId by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            // 导航入口：打开侧边栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Rounded.Menu, contentDescription = "打开导航")
                }
                Text("自定义音源", style = MaterialTheme.typography.titleMedium)
            }
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
                // 合并导入入口：一个按钮 → ActionSheet（本地文件 / URL）
                FilledTonalButton(onClick = { showImportSheet = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("添加音源", modifier = Modifier.padding(start = dimens.spaceXs))
                }
                if (engineStatus is UserApiStatus.Ready) {
                    FilledTonalButton(onClick = onDeactivate) {
                        Icon(Icons.Rounded.Stop, contentDescription = null)
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
                        leadingContent = { Icon(Icons.Rounded.Extension, contentDescription = null) },
                        headlineContent = { Text(api.name + if (isActive) "（使用中）" else "") },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    api.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                                    api.author.takeIf { it.isNotBlank() },
                                    api.description.takeIf { it.isNotBlank() },
                                    api.platforms.takeIf { it.isNotEmpty() }
                                        ?.let { "支持：${it.joinToString("、")}" },
                                .joinToString(" · ").ifBlank { api.id }
                            )
                        },
                        trailingContent = {
                            Row {
                                if (isActive) {
                                    IconButton(onClick = onDeactivate) {
                                        Icon(Icons.Rounded.Stop, contentDescription = "停用")
                                    }
                                } else {
                                    IconButton(onClick = { onActivate(api.id) }) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = "启用")
                                    }
                                }
                                // 测试握手：发一次真实脚本请求验证可用性
                                IconButton(
                                    onClick = {
                                        if (testingId != null) return@IconButton
                                        testingId = api.id
                                        testResult = null
                                        scope.launch {
                                            val started = System.currentTimeMillis()
                                            val msg = runCatching { onTestHandshake(api) }
                                                .getOrElse { "测试异常：${it.message}" }
                                            val elapsed = System.currentTimeMillis() - started
                                            testResult = "$msg（${elapsed}ms）"
                                            testResultId = api.id
                                            testingId = null
                                        }
                                    },
                                    enabled = testingId == null,
                                ) {
                                    if (testingId == api.id) {
                                        CircularProgressIndicator(
                                            Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(Icons.Rounded.NetworkCheck, contentDescription = "测试握手")
                                    }
                                }
                                if (api.sourceUrl != null) {
                                    IconButton(onClick = { onUpdate(api.id) }) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "更新")
                                    }
                                }
                                IconButton(onClick = { removeTarget = api }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "删除")
                                }
                            }
                        },
                    )
                    // 握手测试结果（显示在对应条目正下方）
                    if (testResultId == api.id && testResult != null) {
                        Text(
                            testResult.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (testResult.orEmpty().startsWith("握手成功")) {
                                androidx.compose.ui.graphics.Color(0xFF2E7D32)
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }

    // 导入方式 ActionSheet
    if (showImportSheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showImportSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = dimens.spaceLg)) {
                Text("添加音源", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(dimens.spaceSm))
                SheetActionRow(
                    icon = Icons.Rounded.FileOpen,
                    label = "从本地文件导入",
                    description = "选择设备上的 .js 脚本",
                ) {
                    showImportSheet = false
                    picker.launch(arrayOf("*/*"))
                }
                SheetActionRow(
                    icon = Icons.Rounded.Link,
                    label = "从 URL 导入",
                    description = "输入脚本链接下载",
                ) {
                    showImportSheet = false
                    showUrlDialog = true
                }
                Spacer(Modifier.height(dimens.spaceXl))
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

/** ActionSheet 里的一行操作：图标 + 标题 + 描述。 */
@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppTheme.dimens.spaceMd),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.padding(start = AppTheme.dimens.spaceLg)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}