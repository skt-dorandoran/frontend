package org.duckdns.dorandoran.callaiassistant.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.imePadding
import androidx.navigation.NavController
import org.duckdns.dorandoran.callaiassistant.R
import org.duckdns.dorandoran.callaiassistant.SettingsStore
import org.duckdns.dorandoran.callaiassistant.tts.TtsManager
import org.duckdns.dorandoran.callaiassistant.webrtc.CustomAudioDeviceModule
import org.duckdns.dorandoran.callaiassistant.ui.components.RemoteVoiceWaveMini
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.MessageOrigin
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.ChatMessage
import org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneStore
import org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneTtsApi
import org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcManager
import android.util.Log
import kotlinx.coroutines.launch

private val NOISE_ONLY_REGEX = Regex("^[\\p{Punct}\\s·…]+$")

private fun isMeaningfulConversationText(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isBlank()) return false
    if (normalized.length == 1 && !normalized[0].isLetterOrDigit()) return false
    if (NOISE_ONLY_REGEX.matches(normalized)) return false
    return true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallTypingScreen(
    navController: NavController,
    viewModel: CallViewModel,
    webRtcManager: WebRtcManager,
    onEndCall: () -> Unit
) {
    val callInfo by viewModel.callInfo.collectAsState()
    val messages by viewModel.messages.collectAsState()
    var inputText by remember { mutableStateOf(TextFieldValue()) }
    val isDark = isSystemInDarkTheme()
    val primaryBlue = Color(0xFF2F5BFF)
    val typingBodyBackground = if (isDark) Color(0xFF111316) else Color(0xFFF8FBFF)
    val context = LocalContext.current
    val textScale = SettingsStore.getCallTextScale(context)
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    var messageTts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var isSendingMessage by remember { mutableStateOf(false) }
    var isSendingAiSuggestion by remember { mutableStateOf(false) }
    var isInlineKeypadVisible by remember { mutableStateOf(false) }
    val voiceId = remember { VoiceCloneStore.getVoiceId(context) }
    val isVoiceCloneEnabled = remember { SettingsStore.isVoiceCloneEnabled(context) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }
    val coroutineScope = rememberCoroutineScope()
    val oneClickReplies = remember { SettingsStore.getOneClickReplies(context) }
    val oneClickScrollState = rememberScrollState()
    val visibleOneClickReplies = remember(oneClickReplies) { oneClickReplies.filter { it.isNotBlank() } }
    val displayMessages = remember(messages) {
        messages.filter { isMeaningfulConversationText(it.text) }
    }
    val aiSuggestionTop1 by viewModel.aiSuggestionTop1.collectAsState()
    val aiSuggestionTop2 by viewModel.aiSuggestionTop2.collectAsState()
    val isRefreshingAiSuggestions by viewModel.isRefreshingAiSuggestions.collectAsState()
    val remoteAudioLevel by webRtcManager.remoteAudioLevel.collectAsState()
    val sharedSuggestions = listOf(aiSuggestionTop1, aiSuggestionTop2)

    val listState = rememberLazyListState()
    fun sendMessageNow(rawText: String) {
        val textToSend = rawText.trim()
        if (textToSend.isEmpty() || isSendingMessage || isInlineKeypadVisible) return
        val isAiSuggestionText = sharedSuggestions.contains(textToSend)
        isSendingMessage = true
        isSendingAiSuggestion = isAiSuggestionText
        viewModel.sendMessage(
            textToSend,
            origin = if (isAiSuggestionText) MessageOrigin.AI_SUGGESTION else MessageOrigin.TEXT_MODE
        )
        val markSendDone = {
            isSendingMessage = false
            isSendingAiSuggestion = false
        }
        if (isTtsReady) {
            if (isVoiceCloneEnabled && !voiceId.isNullOrBlank()) {
                val callIdStr = "call_typing_${System.currentTimeMillis()}"
                val contextSafe = context.applicationContext
                coroutineScope.launch {
                    val wavFile = VoiceCloneTtsApi.synthesizeVoiceClone(
                        callId = callIdStr,
                        text = textToSend,
                        voiceId = voiceId,
                        sourceType = "ai_response",
                        context = contextSafe
                    )
                    if (wavFile != null && wavFile.exists()) {
                        org.duckdns.dorandoran.callaiassistant.tts.SherpaOnnxTtsManager.playWavFile(
                            wavFile = wavFile,
                            audioManager = audioManager,
                            onDone = {
                                Log.d("CallTypingScreen", "VoiceClone TTS playback completed for: $textToSend")
                                markSendDone()
                            }
                        )
                    } else {
                        TtsManager.speak(
                            tts = messageTts,
                            text = textToSend,
                            audioManager = audioManager,
                            onDone = {
                                Log.d("CallTypingScreen", "Fallback TTS playback completed for: $textToSend")
                                markSendDone()
                            }
                        )
                    }
                }
            } else {
                TtsManager.speak(
                    tts = messageTts,
                    text = textToSend,
                    audioManager = audioManager,
                    onDone = {
                        Log.d("CallTypingScreen", "TTS playback completed for: $textToSend")
                        markSendDone()
                    }
                )
            }
        } else {
            markSendDone()
        }
    }

    // TTS 초기화 - 통화 시작 시 1회만
    LaunchedEffect(Unit) {
        TtsManager.initializeForCall(
            context = context,
            onReady = { tts ->
                messageTts = tts
                isTtsReady = true
                Log.d("CallTypingScreen", "TTS ready for CallTypingScreen")
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            // TTS 종료하지 말고 (다른 화면에서 사용할 수 있음) 큐만 정리
            CustomAudioDeviceModule.clearTtsQueue()
            messageTts = null
            isTtsReady = false
            toneGenerator.release()
        }
    }

    // 메시지가 추가되면 스크롤을 가장 아래로 즉시 이동
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(index = 0)
        }
    }
    
    // 입력 필드 포커스 시에도 스크롤 유지
    LaunchedEffect(inputText.text) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(index = 0)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(typingBodyBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(typingBodyBackground)
        ) {
        // TopBar (고정)
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    RemoteVoiceWaveMini(
                        level = remoteAudioLevel,
                        modifier = Modifier.size(width = 20.dp, height = 18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = callInfo.phoneNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = callInfo.callTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로가기"
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "통화 종료",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // 메시지 영역 (스크롤 가능)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(typingBodyBackground)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                reverseLayout = true
            ) {
                // 메시지를 역순으로 표시 (최신 메시지가 맨 아래)
                items(displayMessages.size) { index ->
                    val message = displayMessages[displayMessages.size - 1 - index]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
                    ) {
                        if (!message.isFromMe) {
                            RemoteMessageBubble(message = message, textScale = textScale)
                        } else {
                            MyMessageBubble(message = message, textScale = textScale)
                        }
                    }
                }
            }
        }

        // 입력 블럭 바로 위 고정 AI 추천 답변
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(typingBodyBackground)
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = "AI 추천",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isSendingAiSuggestion) "AI 추천 답변 전송 중.." else "AI 추천 답변",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    enabled = !isRefreshingAiSuggestions && !isInlineKeypadVisible,
                    onClick = { viewModel.refreshAiSuggestions() },
                    modifier = Modifier.size(22.dp)
                ) {
                    if (!isRefreshingAiSuggestions) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "추천 새로고침",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            sharedSuggestions.forEach { suggestion ->
                SuggestionButton(
                    text = suggestion,
                    isLoading = isRefreshingAiSuggestions,
                    controlsEnabled = !isInlineKeypadVisible,
                    useGradientBorder = false,
                    modifier = Modifier.fillMaxWidth(0.62f),
                    onClick = {
                        if (isRefreshingAiSuggestions || suggestion.isBlank()) return@SuggestionButton
                        sendMessageNow(suggestion)
                    }
                )
            }
        }

        // BottomBar (고정)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (visibleOneClickReplies.isNotEmpty() && !isInlineKeypadVisible) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(oneClickScrollState)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            visibleOneClickReplies.forEach { reply ->
                                OutlinedButton(
                                    enabled = !isInlineKeypadVisible,
                                    onClick = {
                                        inputText = TextFieldValue(
                                            text = reply,
                                            selection = TextRange(reply.length)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = reply,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // 입력창 (항상 보이도록 오버레이 밖으로 분리)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isInlineKeypadVisible = !isInlineKeypadVisible },
                            modifier = Modifier
                                .height(52.dp)
                                .width(36.dp)
                                .align(Alignment.CenterVertically)
                                .offset(y = 1.dp)
                        ) {
                            TypingKeypadDotsIcon(
                                tint = if (isInlineKeypadVisible) primaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            val fieldShape = RoundedCornerShape(24.dp)
                            val borderColor = Color(0xFFE8E8E8)
                            val inputEnabled = inputText.text.isNotBlank() && !isSendingMessage && !isInlineKeypadVisible

                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                maxLines = 3,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clip(fieldShape)
                                    .border(1.dp, borderColor, fieldShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(start = 16.dp, end = 58.dp)
                            ) { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                if (inputText.text.isEmpty()) {
                                    Text(
                                        text = "AI가 대신 말할 내용을 입력해주세요",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                                }
                            }

                            IconButton(
                                onClick = {
                                    val textToSend = inputText.text.trim()
                                    if (textToSend.isEmpty()) return@IconButton
                                    inputText = TextFieldValue()
                                    sendMessageNow(textToSend)
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(43.2.dp),
                                enabled = inputEnabled
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_send_blue),
                                    contentDescription = "전송",
                                    tint = Color.Unspecified,
                                    modifier = Modifier
                                        .size(32.4.dp)
                                        .alpha(if (inputEnabled) 1f else 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        AnimatedVisibility(
            visible = isInlineKeypadVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 76.dp)
                .zIndex(3f),
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                TypingModeKeypadContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                    onKeyPress = { key ->
                        playTypingModeDtmfTone(toneGenerator, key)
                    }
                )
            }
        }
    }
}


@Composable
private fun SuggestionButton(
    text: String,
    isLoading: Boolean,
    controlsEnabled: Boolean = true,
    useGradientBorder: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF8B7BFF), Color(0xFF66D1C5))
    )
    OutlinedButton(
        onClick = onClick,
        enabled = controlsEnabled && !isLoading && text.isNotBlank(),
        modifier = if (!isLoading && useGradientBorder) {
            modifier.border(width = 1.5.dp, brush = gradientBrush, shape = shape)
        } else {
            modifier
        },
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isLoading) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                Color.White
            },
            contentColor = if (isLoading) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(
            if (!isLoading && useGradientBorder) 0.dp else 1.dp,
            if (isLoading) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            } else if (useGradientBorder) {
                Color.Transparent
            } else {
                Color(0xFFCCCCCC)
            }
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.5.dp)
        )
    }
}

@Composable
private fun TypingModeKeypadContent(
    modifier: Modifier = Modifier,
    onKeyPress: (Char) -> Unit
) {
    val dialPad = listOf(
        listOf(TypingModeDialPadKey("1", "ㄱㅋ", ".QZ"), TypingModeDialPadKey("2", "ㄴ", "ABC"), TypingModeDialPadKey("3", "ㄷㅌ", "DEF")),
        listOf(TypingModeDialPadKey("4", "ㄹ", "GHI"), TypingModeDialPadKey("5", "ㅁ", "JKL"), TypingModeDialPadKey("6", "ㅂㅍ", "NMO")),
        listOf(TypingModeDialPadKey("7", "ㅅ", "PRS"), TypingModeDialPadKey("8", "ㅇ", "TUV"), TypingModeDialPadKey("9", "ㅈㅊ", "WXY")),
        listOf(TypingModeDialPadKey("*", ",", ""), TypingModeDialPadKey("0", "ㅎ", "+"), TypingModeDialPadKey("#", ";", ""))
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        TypingModeDialPadButton(
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

@Composable
private fun TypingModeDialPadButton(
    key: TypingModeDialPadKey,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
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

private data class TypingModeDialPadKey(val digit: String, val hangul: String, val latin: String)

@Composable
private fun TypingKeypadDotsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(tint)
                        )
                    }
                }
            }
        }
    }
}

private fun playTypingModeDtmfTone(toneGenerator: ToneGenerator, key: Char) {
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
