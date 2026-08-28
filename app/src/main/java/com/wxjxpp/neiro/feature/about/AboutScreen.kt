package com.wxjxpp.neiro.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(onOpenDrawer: () -> Unit, contentPadding: PaddingValues = PaddingValues(), modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onOpenDrawer) { Icon(Icons.Rounded.Menu, "打开侧边栏") }
            Text("关于", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFFE9E4FF), MaterialTheme.colorScheme.primary.copy(alpha = .55f), MaterialTheme.colorScheme.tertiary.copy(alpha = .35f), Color(0xFFFFEAF3))))) {
            Column(Modifier.align(Alignment.BottomStart).padding(26.dp)) {
                Text("Neiro", color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text("1.0.2  ·  Shell++", color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.titleMedium)
            }
        }
        SectionTitle("贡献者"); InfoCard { Contributor("Wxjxpp", "主要开发"); Contributor("Hoper", "捧场"); Contributor("binsys", "反馈体验") }
        SectionTitle("开源组件"); InfoCard {
            OpenSourceItem("kotlinx.coroutines", "Kotlin 协程与结构化并发")
            OpenSourceItem("accompanist-lyrics-ui", "灵动的歌词渲染组件")
            OpenSourceItem("Media3", "音频播放内核"); OpenSourceItem("Room", "本地音乐库与数据持久化"); OpenSourceItem("Coil", "封面图片加载")
        }
        Spacer(Modifier.height(24.dp))
    }
}
@Composable private fun SectionTitle(text: String) = Text(text, Modifier.padding(start = 16.dp, top = 26.dp, bottom = 10.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
@Composable private fun InfoCard(content: @Composable ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(vertical = 10.dp), content = content) }
@Composable private fun Contributor(name: String, role: String) = Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)); Spacer(Modifier.width(16.dp)); Column { Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(role, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun OpenSourceItem(name: String, description: String) = Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(3.dp)); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }