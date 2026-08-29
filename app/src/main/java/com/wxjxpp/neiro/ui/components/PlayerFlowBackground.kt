package com.wxjxpp.neiro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Player-only palette flow adapted from RawS; animation is owned by the expanded player. */
@Composable
fun NeiroPlayerFlowBackground(
    colors: List<Color>,
    fallback: Color,
    coverUri: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = remember(colors, fallback) { (colors.take(5) + fallback).distinct().take(5) }
    val transition = rememberInfiniteTransition(label = "player-flow")
    val phase by transition.animateFloat(
        0f,
        (2f * PI).toFloat(),
        infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "player-flow-phase",
    )
    Box(modifier = modifier) {
        AsyncImage(
            model = coverUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.35f
                scaleY = 1.35f
            },
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = max(size.width, size.height) * 0.72f
            palette.forEachIndexed { index, color ->
                val orbit = phase * if (index % 2 == 0) 1f else -0.72f + index * 1.35f
                val center = Offset(
                    size.width * (0.18f + index * 0.17f) + cos(orbit) * size.width * 0.16f,
                    size.height * (0.18f + (index % 3) * 0.31f) + sin(orbit * 0.83f) * size.height * 0.18f,
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(color.copy(alpha = 0.62f), Color.Transparent), center, radius),
                    radius = radius,
                    center = center,
                )
            }
        }
    }
}
