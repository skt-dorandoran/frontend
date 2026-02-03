package org.duckdns.dorandoran.callaiassistant.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class CallState {
    DIALING,    // 연결 중
    ACTIVE,     // 통화 중
    ENDED       // 통화 종료
}

@Composable
fun InCallScreen(
    phoneNumber: String,
    contactName: String?,
    callState: CallState,
    callDurationSeconds: Long,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName = contactName?.takeIf { it.isNotBlank() } ?: formatPhoneNumber(phoneNumber)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(0.5f))

        // 프로필/아바타 영역
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            when (callState) {
                CallState.DIALING -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "dialing")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .alpha(alpha),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                CallState.ACTIVE, CallState.ENDED -> Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 통화 대상 이름
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (contactName != null && phoneNumber.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatPhoneNumber(phoneNumber),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 통화 상태/시간
        Text(
            text = when (callState) {
                CallState.DIALING -> "연결 중..."
                CallState.ACTIVE -> formatDuration(callDurationSeconds)
                CallState.ENDED -> "통화 종료"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // 통화 종료 버튼
        if (callState != CallState.ENDED) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable(onClick = onEndCall),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "통화 종료",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatPhoneNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return when {
        digits.length <= 3 -> digits
        digits.length <= 7 -> "${digits.take(3)}-${digits.drop(3)}"
        else -> "${digits.take(3)}-${digits.drop(3).take(4)}-${digits.drop(7)}"
    }
}

private fun formatDuration(seconds: Long): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%02d:%02d".format(min, sec)
}
