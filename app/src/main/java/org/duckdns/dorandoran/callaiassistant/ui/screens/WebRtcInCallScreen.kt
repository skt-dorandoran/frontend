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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState

@Composable
fun WebRtcInCallScreen(
    phoneNumber: String,
    connectionState: WebRtcConnectionState,
    callDurationSeconds: Long,
    logMessages: List<String> = emptyList(),
    onEndCall: () -> Unit,
    onSpeakerphoneToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSpeakerphoneOn by remember { mutableStateOf<Boolean>(false) }
    val displayNumber = if (phoneNumber.isNotBlank()) formatPhoneNumber(phoneNumber) else "상대방"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(0.5f))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            when (connectionState) {
                WebRtcConnectionState.DISCONNECTED -> {}
                WebRtcConnectionState.CONNECTED -> {
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
                        modifier = Modifier.size(56.dp).alpha(alpha),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                WebRtcConnectionState.IN_CALL -> Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = displayNumber,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (connectionState) {
                WebRtcConnectionState.DISCONNECTED -> "통화 종료"
                WebRtcConnectionState.CONNECTED -> "연결 중..."
                WebRtcConnectionState.IN_CALL -> formatDuration(callDurationSeconds)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        if (logMessages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                logMessages.takeLast(10).forEach { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (connectionState != WebRtcConnectionState.DISCONNECTED) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 스피커폰 토글 버튼
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSpeakerphoneOn)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                isSpeakerphoneOn = !isSpeakerphoneOn
                                onSpeakerphoneToggle(isSpeakerphoneOn)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSpeakerphoneOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = "스피커폰",
                            modifier = Modifier.size(36.dp),
                            tint = if (isSpeakerphoneOn)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "스피커폰",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 통화 종료 버튼
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .clickable(onClick = onEndCall),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "통화 종료",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "통화 종료",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatPhoneNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return when {
        digits.isEmpty() -> "상대방"
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
