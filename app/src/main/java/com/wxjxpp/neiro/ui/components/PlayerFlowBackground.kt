package com.wxjxpp.neiro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Lightweight player-only adaptation of RawS's palette-driven moving flow. */
@Composable
fun NeiroPlayerFlowBackground(
    colors: List<Color>,
    fallback: Color,
    coverUri: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = remember(colors, fallback) {
        (colors.take(5) + fallback).distinct().take(5)
    }
    val transition = rememberInfiniteTransition(label = "player-flow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "player-flow-phase",
    )
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        AsyncImage(
            model = coverUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.35f
                scaleY = 1.35f
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(72f, 72f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                }
            },
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val radius = max(w, h) * 0.72f
        palette.forEachIndexed { index, color ->
            val orbit = phase * (if (index % 2 == 0) 1f else -0.72f) + index * 1.35f
            val x = w * (0.18f + index * 0.17f) + cos(orbit) * w * 0.16f
            val y = h * (0.18f + (index % 3) * 0.31f) + sin(orbit * 0.83f) * h * 0.18f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.78f), color.copy(alpha = 0f)),
                    center = Offset(x, y),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(x, y),
            )
        }
    }
}

