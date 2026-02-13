package org.duckdns.dorandoran.callaiassistant.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import org.duckdns.dorandoran.callaiassistant.SettingsStore
import org.duckdns.dorandoran.callaiassistant.tts.TtsManager
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme
import org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState

@Composable
fun WebRtcInCallScreen(
    phoneNumber: String,
    connectionState: WebRtcConnectionState,
    callDurationSeconds: Long,
    logMessages: List<String> = emptyList(),
    sttText: String = "",
    aiSuggestions: List<String> = listOf("잠시만요, 다시 말씀해주실 수 있나요?", "네, 확인했습니다. 바로 처리하겠습니다."),
    onSendAiSuggestion: (String) -> Unit = {},
    onEndCall: () -> Unit,
    onSpeakerphoneToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSpeakerphoneOn by remember { mutableStateOf<Boolean>(false) }
    var selectedMode by remember { mutableStateOf(CallMode.DIRECT) }
    var callScreenState by remember { mutableStateOf(CallScreenState.MODE_SELECT) }
    var suggestionSetIndex by remember { mutableStateOf(0) }
    var selectedSuggestionIndex by remember { mutableStateOf<Int?>(null) }
    var userInputText by remember { mutableStateOf("") }
    var isSendingMessage by remember { mutableStateOf(false) }
    val isAiCorrectionMode = selectedMode == CallMode.AI_CORRECTION
    val isKeypadActive = callScreenState == CallScreenState.KEYPAD
    val coroutineScope = rememberCoroutineScope()
    var messageTts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    val suggestionSets = remember(aiSuggestions) {
        listOf(
            aiSuggestions.take(2).ifEmpty {
                listOf("잠시만요, 다시 말씀해주실 수 있나요?", "네, 확인했습니다. 바로 처리하겠습니다.")
            },
            listOf("조금만 기다려 주세요.", "지금 바로 확인해서 알려드릴게요.")
        )
    }
    val currentSuggestions = suggestionSets[suggestionSetIndex % suggestionSets.size]
    val displayNumber = if (phoneNumber.isNotBlank()) formatPhoneNumber(phoneNumber) else "상대방"
    val textScale = remember { SettingsStore.getCallTextScale(context) }
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF0B0B0C) else Color(0xFFF6F6F9)
    val cardColor = if (isDark) Color(0xFF16161A) else Color(0xFFFFFFFF)
    val secondaryTextColor = if (isDark) Color(0xFFB0B0B6) else Color(0xFF8E8E93)
    val primaryBlue = Color(0xFF2F5BFF)
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }

    DisposableEffect(Unit) {
        onDispose {
            toneGenerator.release()
            TtsManager.shutdown(messageTts)
            messageTts = null
        }
    }

    BackHandler(enabled = callScreenState == CallScreenState.KEYPAD) {
        callScreenState = CallScreenState.MODE_SELECT
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = when (connectionState) {
                        WebRtcConnectionState.DISCONNECTED -> "통화 종료"
                        WebRtcConnectionState.CONNECTED -> "연결 중..."
                        WebRtcConnectionState.IN_CALL -> "통화 시간 ${formatDuration(callDurationSeconds)}"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * textScale
                    ),
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = displayNumber,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * textScale
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when (callScreenState) {
                CallScreenState.MODE_SELECT -> {
                    if (isAiCorrectionMode) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else if (selectedMode == CallMode.DIRECT && connectionState == WebRtcConnectionState.IN_CALL) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (sttText.isNotBlank()) sttText else "상대방의 말이 여기에 표시됩니다",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI 추천",
                                        tint = primaryBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "AI 추천 답변",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        suggestionSetIndex = (suggestionSetIndex + 1) % suggestionSets.size
                                        selectedSuggestionIndex = null
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "추천 새로고침",
                                        tint = secondaryTextColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                currentSuggestions.forEachIndexed { index, suggestion ->
                                    val selected = selectedSuggestionIndex == index
                                    OutlinedButton(
                                        onClick = {
                                            selectedSuggestionIndex = index
                                            userInputText = suggestion
                                            onSendAiSuggestion(suggestion)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) primaryBlue else MaterialTheme.colorScheme.outline
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selected) primaryBlue.copy(alpha = 0.08f) else Color.Transparent,
                                            contentColor = MaterialTheme.colorScheme.onBackground
                                        )
                                    ) {
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                CallScreenState.KEYPAD -> {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (callScreenState == CallScreenState.KEYPAD) {
                            Modifier.fillMaxHeight(0.75f)
                        } else {
                            Modifier
                        }
                    )
                    .navigationBarsPadding()
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        clip = false
                    )
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(cardColor)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (callScreenState == CallScreenState.MODE_SELECT) {
                        if (connectionState == WebRtcConnectionState.IN_CALL && selectedMode == CallMode.DIRECT) {
                            OutlinedTextField(
                                value = userInputText,
                                onValueChange = { userInputText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                placeholder = {
                                    Text(
                                        text = "직접 말씀하시거나\n위의 추천 답변을 선택하세요",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryTextColor
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    focusedIndicatorColor = primaryBlue,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = "전화 모드를 선택해주세요",
                                style = MaterialTheme.typography.labelLarge,
                                color = secondaryTextColor,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (connectionState == WebRtcConnectionState.IN_CALL && 
                            selectedMode == CallMode.DIRECT && 
                            userInputText.isNotBlank()) {
                            // 보내기 버튼 (텍스트 입력 시)
                            Button(
                                onClick = {
                                    val textToSend = userInputText
                                    isSendingMessage = true
                                    
                                    // TTS 초기화 및 재생
                                    messageTts = TtsManager.initializeForCall(
                                        context = context,
                                        onReady = { tts ->
                                            messageTts = tts
                                            TtsManager.speak(tts, textToSend, audioManager)
                                        },
                                        onDone = {
                                            coroutineScope.launch {
                                                delay(300)
                                                TtsManager.shutdown(messageTts)
                                                messageTts = null
                                                userInputText = ""
                                                isSendingMessage = false
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSendingMessage) MaterialTheme.colorScheme.surfaceVariant else primaryBlue,
                                    contentColor = if (isSendingMessage) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else Color.White
                                ),
                                enabled = !isSendingMessage,
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Text("보내기")
                            }
                        } else {
                            // 모드 선택 버튼들
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { selectedMode = CallMode.DIRECT },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedMode == CallMode.DIRECT) primaryBlue else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (selectedMode == CallMode.DIRECT) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text("직접 말하기")
                                }

                                Button(
                                    onClick = { selectedMode = CallMode.AI_CORRECTION },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedMode == CallMode.AI_CORRECTION) primaryBlue else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (selectedMode == CallMode.AI_CORRECTION) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text("AI 교정")
                                }

                                Button(
                                    onClick = { selectedMode = CallMode.TEXT },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedMode == CallMode.TEXT) primaryBlue else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (selectedMode == CallMode.TEXT) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text("텍스트 통화")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { change, _ ->
                                        change.consume()
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        KeypadScreenContent(
                            modifier = Modifier.fillMaxWidth(),
                            onKeyPress = { key ->
                                playDtmfTone(toneGenerator, key)
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSpeakerphoneOn) primaryBlue.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                                .clickable {
                                    isSpeakerphoneOn = !isSpeakerphoneOn
                                    onSpeakerphoneToggle(isSpeakerphoneOn)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "스피커",
                                tint = if (isSpeakerphoneOn) primaryBlue else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "스피커",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
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
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isKeypadActive) primaryBlue.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                                .clickable {
                                    callScreenState = if (callScreenState == CallScreenState.KEYPAD) {
                                        CallScreenState.MODE_SELECT
                                    } else {
                                        CallScreenState.KEYPAD
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            KeypadDotsIcon(
                                tint = if (isKeypadActive) primaryBlue else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "숫자 키패드",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private enum class CallScreenState {
    MODE_SELECT,
    KEYPAD
}

private enum class CallMode {
    DIRECT,
    AI_CORRECTION,
    TEXT
}

@Composable
private fun KeypadScreenContent(
    modifier: Modifier = Modifier,
    onKeyPress: (Char) -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        val dialPad = listOf(
            listOf(InCallDialPadKey("1", "ㄱㅋ", ".QZ"), InCallDialPadKey("2", "ㄴ", "ABC"), InCallDialPadKey("3", "ㄷㅌ", "DEF")),
            listOf(InCallDialPadKey("4", "ㄹ", "GHI"), InCallDialPadKey("5", "ㅁ", "JKL"), InCallDialPadKey("6", "ㅂㅍ", "NMO")),
            listOf(InCallDialPadKey("7", "ㅅ", "PRS"), InCallDialPadKey("8", "ㅇ", "TUV"), InCallDialPadKey("9", "ㅈㅊ", "WXY")),
            listOf(InCallDialPadKey("*", ",", ""), InCallDialPadKey("0", "ㅎ", "+"), InCallDialPadKey("#", ";", ""))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            dialPad.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            InCallDialPadButton(
                                key = key,
                                onClick = {
                                    key.digit.firstOrNull()?.let { onKeyPress(it) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InCallDialPadButton(
    key: InCallDialPadKey,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.digit,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (key.hangul.isNotBlank() || key.latin.isNotBlank()) {
                Text(
                    text = key.hangul,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = key.latin.ifBlank { " " },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (key.latin.isBlank()) Color.Transparent else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private data class InCallDialPadKey(val digit: String, val hangul: String, val latin: String)

@Composable
private fun KeypadDotsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(tint)
                    )
                }
            }
        }
    }
}

private fun playDtmfTone(toneGenerator: ToneGenerator, key: Char) {
    val tone = when (key) {
        '1' -> ToneGenerator.TONE_DTMF_1
        '2' -> ToneGenerator.TONE_DTMF_2
        '3' -> ToneGenerator.TONE_DTMF_3
        '4' -> ToneGenerator.TONE_DTMF_4
        '5' -> ToneGenerator.TONE_DTMF_5
        '6' -> ToneGenerator.TONE_DTMF_6
        '7' -> ToneGenerator.TONE_DTMF_7
        '8' -> ToneGenerator.TONE_DTMF_8
        '9' -> ToneGenerator.TONE_DTMF_9
        '0' -> ToneGenerator.TONE_DTMF_0
        '*' -> ToneGenerator.TONE_DTMF_S
        '#' -> ToneGenerator.TONE_DTMF_P
        else -> null
    }
    tone?.let { toneGenerator.startTone(it, 140) }
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

@Preview(showBackground = true)
@Composable
fun WebRtcInCallScreenConnectedPreview() {
    CallaiassistantTheme {
        WebRtcInCallScreen(
            phoneNumber = "01012345678",
            connectionState = WebRtcConnectionState.CONNECTED,
            callDurationSeconds = 0,
            onEndCall = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WebRtcInCallScreenInCallPreview() {
    CallaiassistantTheme {
        WebRtcInCallScreen(
            phoneNumber = "01012345678",
            connectionState = WebRtcConnectionState.IN_CALL,
            callDurationSeconds = 125,
            logMessages = listOf(
                "Connecting to WebRTC server...",
                "Offer sent.",
                "ICE candidate received.",
                "Call established."
            ),
            onEndCall = {}
        )
    }
}
