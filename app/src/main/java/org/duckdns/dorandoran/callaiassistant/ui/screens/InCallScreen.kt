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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import org.duckdns.dorandoran.callaiassistant.SettingsStore
import org.duckdns.dorandoran.callaiassistant.util.formatPhoneNumberByRule

enum class CallState {
    DIALING,    // 발신 연결 중
    RINGING,    // 수신 전화 벨 울림
    ACTIVE,     // 통화 중
    ENDED       // 통화 종료
}

@Composable
fun InCallScreen(
    phoneNumber: String,
    contactName: String?,
    callState: CallState,
    callDurationSeconds: Long,
    onAnswerCall: (() -> Unit)? = null,
    onSpeakText: ((String) -> Unit)? = null,
    isSpeakerOn: Boolean = false,
    onToggleSpeaker: (() -> Unit)? = null,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    var ttsInputText by remember { mutableStateOf("") }
    val displayName = contactName?.takeIf { it.isNotBlank() } ?: formatPhoneNumber(phoneNumber)
    val context = LocalContext.current
    val textScale = remember { SettingsStore.getCallTextScale(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                CallState.DIALING, CallState.RINGING -> {
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
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = MaterialTheme.typography.headlineMedium.fontSize * textScale
            ),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (contactName != null && phoneNumber.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatPhoneNumber(phoneNumber),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 통화 상태/시간
        Text(
            text = when (callState) {
                CallState.DIALING -> "연결 중..."
                CallState.RINGING -> "전화가 왔습니다"
                CallState.ACTIVE -> formatDuration(callDurationSeconds)
                CallState.ENDED -> "통화 종료"
            },
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = MaterialTheme.typography.titleMedium.fontSize * textScale
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // TTS 입력창 (통화 중일 때만 표시)
        if (callState == CallState.ACTIVE && onSpeakText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = ttsInputText,
                    onValueChange = { ttsInputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("텍스트를 입력하세요") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val text = ttsInputText.trim()
                        if (text.isNotBlank()) {
                            onSpeakText(text)
                            ttsInputText = ""
                        }
                    }
                ) {
                    Text("입력")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // 통화 받기 버튼 (수신 전화 시에만 표시)
        if (callState == CallState.RINGING && onAnswerCall != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onAnswerCall),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "통화 받기",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "통화 받기",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 스피커폰 / 통화 종료 버튼
        if (callState != CallState.ENDED) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 스피커폰 버튼 (통화 중일 때만)
                if (callState == CallState.ACTIVE && onToggleSpeaker != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSpeakerOn) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(onClick = onToggleSpeaker),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "스피커폰",
                                modifier = Modifier.size(36.dp),
                                tint = if (isSpeakerOn) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "스피커폰",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
    return formatPhoneNumberByRule(number)
}

private fun formatDuration(seconds: Long): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%02d:%02d".format(min, sec)
}
