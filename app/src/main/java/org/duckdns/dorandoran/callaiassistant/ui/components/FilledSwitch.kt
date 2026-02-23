package org.duckdns.dorandoran.callaiassistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun FilledSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    checkedTrackColor: Color,
    uncheckedTrackColor: Color,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.White
) {
    val settleSpec = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow
    )
    val tapSpec = tween<Float>(
        durationMillis = 170,
        easing = FastOutSlowInEasing
    )
    val externalSyncSpec = tween<Float>(
        durationMillis = 140,
        easing = LinearOutSlowInEasing
    )

    val switchWidth = 52.dp
    val switchHeight = 32.dp
    val thumbSize = 24.dp
    val padding = 4.dp
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxOffsetPx = with(density) { (switchWidth - thumbSize - (padding * 2)).toPx() }
    var isDragging by remember { mutableStateOf(false) }
    var expectedChecked by remember { mutableStateOf<Boolean?>(null) }
    val thumbOffsetPx = remember { Animatable(0f) }

    LaunchedEffect(maxOffsetPx) {
        val target = if (checked) maxOffsetPx else 0f
        thumbOffsetPx.snapTo(target)
    }

    LaunchedEffect(checked, maxOffsetPx, isDragging) {
        if (isDragging) return@LaunchedEffect
        if (expectedChecked != null && expectedChecked != checked) return@LaunchedEffect
        expectedChecked = null
        val target = if (checked) maxOffsetPx else 0f
        if (abs(thumbOffsetPx.value - target) < 0.5f) {
            thumbOffsetPx.snapTo(target)
        } else {
            thumbOffsetPx.animateTo(target, animationSpec = externalSyncSpec)
        }
    }

    val visualOffsetPx = thumbOffsetPx.value
    val progress = (visualOffsetPx / maxOffsetPx).coerceIn(0f, 1f)
    val trackColor = lerp(uncheckedTrackColor, checkedTrackColor, progress)

    Box(
        modifier = modifier
            .size(width = switchWidth, height = switchHeight)
            .clip(CircleShape)
            .background(trackColor)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        thumbOffsetPx.snapTo((thumbOffsetPx.value + delta).coerceIn(0f, maxOffsetPx))
                    }
                },
                onDragStarted = {
                    isDragging = true
                    expectedChecked = null
                },
                onDragStopped = {
                    isDragging = false
                    val finalOffset = thumbOffsetPx.value
                    val nextChecked = finalOffset > (maxOffsetPx / 2f)
                    expectedChecked = nextChecked
                    scope.launch {
                        thumbOffsetPx.animateTo(
                            if (nextChecked) maxOffsetPx else 0f,
                            animationSpec = settleSpec
                        )
                    }
                    if (nextChecked != checked) onCheckedChange(nextChecked)
                }
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                val nextChecked = !checked
                expectedChecked = nextChecked
                onCheckedChange(nextChecked)
                scope.launch {
                    thumbOffsetPx.animateTo(
                        if (nextChecked) maxOffsetPx else 0f,
                        animationSpec = tapSpec
                    )
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = padding),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = with(density) { visualOffsetPx.toDp() })
                    .size(thumbSize)
                    .background(thumbColor, CircleShape)
            )
        }
    }
}
