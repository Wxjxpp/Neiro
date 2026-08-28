package com.wxjxpp.neiro.feature.about

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.shader.RuntimeShader
import top.yukonga.miuix.kmp.shader.asBrush
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

private const val MIUIX_BG_SHADER = """
uniform float2 uResolution; uniform float uTime;
half4 main(float2 fragCoord) {
 float2 p=fragCoord/uResolution; float t=uTime*.35;
 float2 a=float2(.18+.18*sin(t),.25+.12*cos(t*.8));
 float2 b=float2(.82+.12*cos(t*.7),.72+.16*sin(t*.9));
 float2 c=float2(.52+.20*sin(t*.55),.40+.18*cos(t*.65));
 float wa=exp(-distance(p,a)*3.8), wb=exp(-distance(p,b)*3.2), wc=exp(-distance(p,c)*3.6);
 float3 rgb=float3(.91,.89,1.)+float3(.45,.48,1.)*wa*.42+float3(1.,.52,.72)*wb*.25+float3(.35,.72,1.)*wc*.28;
 return half4(clamp(rgb,0.,1.),1.);
}
"""

@Composable
fun AboutScreen(onOpenDrawer: () -> Unit, contentPadding: PaddingValues = PaddingValues(), modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onOpenDrawer) { Icon(Icons.Rounded.Menu, "打开侧边栏") }
            Text("关于", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp)); MiuixGlowCard()
        SectionTitle("贡献者"); InfoCard { Contributor("Wxjxpp", "主要开发"); Contributor("Hoper", "捧场"); Contributor("binsys", "反馈体验") }
        SectionTitle("开源组件"); InfoCard {
            OpenSourceItem("kotlinx.coroutines", "Kotlin 协程与结构化并发")
            OpenSourceItem("miuix", "Android 原生动态流光背景")
            OpenSourceItem("accompanist-lyrics-ui", "灵动的歌词渲染组件")
            OpenSourceItem("Media3", "音频播放内核"); OpenSourceItem("Room", "本地音乐库与数据持久化"); OpenSourceItem("Coil", "封面图片加载")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun MiuixGlowCard() {
    val transition = rememberInfiniteTransition(label = "miuix_about_glow")
    val time by transition.animateFloat(0f, 100f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart), label = "shader_time")
    val supported = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isRuntimeShaderSupported() }
    val shader = if (supported) remember { RuntimeShader(MIUIX_BG_SHADER) } else null
    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(30.dp)).then(if (shader != null) Modifier.drawWithCache {
        shader.setFloatUniform("uResolution", size.width, size.height); shader.setFloatUniform("uTime", time)
        onDrawBehind { drawRect(shader.asBrush()) }
    } else Modifier.background(MaterialTheme.colorScheme.primaryContainer))) {
        Column(Modifier.align(Alignment.BottomStart).padding(26.dp)) {
            Text("Neiro", color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text("0.4.7  ·  Shell++", color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable private fun SectionTitle(text: String) = Text(text, Modifier.padding(start = 16.dp, top = 26.dp, bottom = 10.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
@Composable private fun InfoCard(content: @Composable ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(vertical = 10.dp), content = content) }
@Composable private fun Contributor(name: String, role: String) = Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)); Spacer(Modifier.width(16.dp)); Column { Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(role, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun OpenSourceItem(name: String, description: String) = Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(3.dp)); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }