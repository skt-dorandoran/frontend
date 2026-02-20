package org.duckdns.dorandoran.callaiassistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun RemoteVoiceWaveMini(
    level: Float,
    modifier: Modifier = Modifier,
    minBarHeight: Dp = 4.dp
) {
    val infinite = rememberInfiniteTransition(label = "remoteWave")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2f).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "remoteWavePhase"
    )
    val clampedLevel = level.coerceIn(0f, 1f)
    val profile = floatArrayOf(0.36f, 0.68f, 1f, 0.68f, 0.4f)
    val barColors = listOf(
        Color(0xFF8B79F6),
        Color(0xFF7B8CF8),
        Color(0xFF6BA3F8),
        Color(0xFF67BCCF),
        Color(0xFF7ADFD0)
    )

    Canvas(modifier = modifier) {
        val bars = profile.size
        val barWidth = size.width / 9f
        val gap = barWidth * 0.78f
        val totalWidth = bars * barWidth + (bars - 1) * gap
        var x = (size.width - totalWidth) / 2f
        val minHeightPx = minBarHeight.toPx()

        repeat(bars) { index ->
            val idle = (sin(phase + (index * 0.7f)) * 0.5f + 0.5f) * 0.26f + 0.08f
            val activity = idle + clampedLevel * 0.92f
            val barHeight = (size.height * (0.18f + profile[index] * activity))
                .coerceIn(minHeightPx, size.height)
            val top = (size.height - barHeight) / 2f

            drawRoundRect(
                color = barColors[index],
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(x = barWidth, y = barWidth)
            )
            x += barWidth + gap
        }
    }
}
