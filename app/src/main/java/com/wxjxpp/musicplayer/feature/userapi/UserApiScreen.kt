package com.wxjxpp.musicplayer.feature.userapi

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wxjxpp.musicplayer.core.userapi.UserApiInfo
import com.wxjxpp.musicplayer.ui.theme.AppTheme

/**
 * 自定义音源管理页。
 *
 * 通过系统文件选择器导入 `.js` 脚本，导入后立即启用。
 * 脚本在 QuickJS 沙箱内执行，网络请求由宿主代发。
 */
@Composable
fun UserApiScreen(
    apis: List<UserApiInfo>,
    onImport: (String) -> Unit,
    onActivate: (String) -> Unit,
    onRemove: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()?.let(onImport)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自定义音源", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "导入 LX 格式的 .js 音源脚本，在沙箱内执行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("导入") }
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
                                IconButton(onClick = { onRemove(api.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}