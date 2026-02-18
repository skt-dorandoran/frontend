package org.duckdns.dorandoran.callaiassistant.ui.screens

import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.imePadding
import androidx.navigation.NavController
import org.duckdns.dorandoran.callaiassistant.SettingsStore
import org.duckdns.dorandoran.callaiassistant.tts.TtsManager
import org.duckdns.dorandoran.callaiassistant.webrtc.CustomAudioDeviceModule
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.ChatMessage
import org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneStore
import org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneTtsApi
import android.util.Log
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallTypingScreen(
    navController: NavController,
    viewModel: CallViewModel,
    onEndCall: () -> Unit
) {
    val callInfo by viewModel.callInfo.collectAsState()
    val messages by viewModel.messages.collectAsState()
    var inputText by remember { mutableStateOf(TextFieldValue()) }
    var suggestionSetIndex by remember { mutableStateOf(0) }
    val isDark = isSystemInDarkTheme()
    val primaryBlue = Color(0xFF2F5BFF)
    val context = LocalContext.current
    val textScale = SettingsStore.getCallTextScale(context)
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    var messageTts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    val voiceId = remember { VoiceCloneStore.getVoiceId(context) }
    val isVoiceCloneEnabled = remember { SettingsStore.isVoiceCloneEnabled(context) }
    val coroutineScope = rememberCoroutineScope()
    
    val listState = rememberLazyListState()

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
            // 통화 종료 시 history 초기화
            viewModel.clearHistory()
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
    
    val suggestionSets = listOf(
        listOf(
            "예약 시간 문의드려요",
            "진료확인서 발급 방법 알려주세요",
            "접수 마감이 몇시인가요?"
        ),
        listOf(
            "오늘 진료 가능할까요?",
            "초진 접수 절차 알려주세요",
            "보험 청구서 발급되나요?"
        )
    )
    val aiSuggestions = suggestionSets[suggestionSetIndex % suggestionSets.size]

    Column(
        modifier = Modifier.fillMaxSize()
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            reverseLayout = true
        ) {
            // 메시지를 역순으로 표시 (최신 메시지가 맨 아래)
            items(messages.size) { index ->
                val message = messages[messages.size - 1 - index]
                if (!message.isFromMe) {
                    RemoteMessageBubble(message = message, textScale = textScale)
                } else {
                    MyMessageBubble(message = message, textScale = textScale)
                }
            }
        }

        // BottomBar (고정)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // AI 추천 답변 섹션 (고정)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "✨",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "AI 추천 답변",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { suggestionSetIndex = (suggestionSetIndex + 1) % suggestionSets.size }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "추천 새로고침",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 추천 답변 버튼들
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    aiSuggestions.forEach { suggestion ->
                        SuggestionButton(
                            text = suggestion,
                            onClick = {
                                inputText = TextFieldValue(
                                    text = suggestion,
                                    selection = TextRange(suggestion.length)
                                )
                            }
                        )
                    }
                }
                
                // 입력창
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        placeholder = {
                            Text(
                                text = "AI가 대신 말할 내용을 입력해주세요",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        maxLines = 3
                    )
                    IconButton(
                        onClick = {
                            val textToSend = inputText.text.trim()
                            if (textToSend.isEmpty()) return@IconButton
                            viewModel.sendMessage(textToSend)
                            inputText = TextFieldValue()
                            
                            // TTS로 메시지 재생
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
                                                }
                                            )
                                        } else {
                                            TtsManager.speak(
                                                tts = messageTts,
                                                text = textToSend,
                                                audioManager = audioManager,
                                                onDone = {
                                                    Log.d("CallTypingScreen", "Fallback TTS playback completed for: $textToSend")
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
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (inputText.text.isNotBlank()) primaryBlue else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "전송",
                            tint = if (inputText.text.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun SuggestionButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
    }
}
