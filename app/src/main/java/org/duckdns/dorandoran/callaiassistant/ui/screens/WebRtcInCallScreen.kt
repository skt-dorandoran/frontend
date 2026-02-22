package org.duckdns.dorandoran.callaiassistant.ui.screens

import androidx.compose.runtime.collectAsState
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.duckdns.dorandoran.callaiassistant.SettingsStore
import org.duckdns.dorandoran.callaiassistant.tts.TtsManager
import org.duckdns.dorandoran.callaiassistant.tts.SherpaOnnxTtsManager
import org.duckdns.dorandoran.callaiassistant.stt.RemoteSttApi
import org.duckdns.dorandoran.callaiassistant.stt.TempWavFileFactory
import org.duckdns.dorandoran.callaiassistant.webrtc.CustomAudioDeviceModule
import org.duckdns.dorandoran.callaiassistant.ui.components.RemoteVoiceWaveMini
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme
import org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState
import org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcManager
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.MessageOrigin
import androidx.lifecycle.viewmodel.compose.viewModel
import org.duckdns.dorandoran.callaiassistant.R
import org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneTtsApi
import java.io.File
// --- 파일 최상위에 선언: 말풍선 컴포저블 ---
@Composable
public fun MyMessageBubble(message: org.duckdns.dorandoran.callaiassistant.ui.viewmodel.ChatMessage, textScale: Float) {
    val bubbleShape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val aiGradientBorder = Brush.horizontalGradient(
        colors = listOf(Color(0xFF8B7BFF), Color(0xFF66D1C5))
    )
    val isAiGeneratedMessage = message.origin == MessageOrigin.AI_SUGGESTION
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .then(
                if (!isAiGeneratedMessage) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = bubbleShape,
                        ambientColor = Color.Black.copy(alpha = 0.40f),
                        spotColor = Color.Black.copy(alpha = 0.40f),
                        clip = false
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (isAiGeneratedMessage) {
                    Modifier.border(width = 1.5.dp, brush = aiGradientBorder, shape = bubbleShape)
                } else {
                    Modifier
                }
            )
            .clip(bubbleShape)
            .background(Color(0xFFEEF5FF))
    ) {
        Text(
            text = message.text,
            color = Color(0xFF111111),
            fontSize = (16 * textScale).sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
public fun RemoteMessageBubble(message: org.duckdns.dorandoran.callaiassistant.ui.viewmodel.ChatMessage, textScale: Float) {
    androidx.compose.material3.Surface(
        color = Color(0xFFFFFFFF),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .shadow(
                elevation = 10.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.50f),
                spotColor = Color.Black.copy(alpha = 0.50f),
                clip = false
            )
    ) {
        Text(
            text = message.text,
            color = androidx.compose.ui.graphics.Color.Black,
            fontSize = (16 * textScale).sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun WebRtcInCallScreen(
    phoneNumber: String,
    connectionState: WebRtcConnectionState,
    callDurationSeconds: Long,
    logMessages: List<String> = emptyList(),
    onSendAiSuggestion: (String) -> Unit = {},
    onDirectMessageSent: (String) -> Unit = {},
    onEndCall: () -> Unit,
    onSpeakerphoneToggle: (Boolean) -> Unit = {},
    onLocalAudioTransmissionToggle: (Boolean) -> Unit = {},
    webRtcManager: WebRtcManager,
    enableIntroPromptPlayback: Boolean = true,
    navController: NavController? = null,
    modifier: Modifier = Modifier,
    viewModel: CallViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val hasPlayedIntroPrompt by viewModel.introPromptPlayed.collectAsState()
    var selectedMode by remember { mutableStateOf(CallMode.DIRECT) }
    var callScreenState by remember { mutableStateOf(CallScreenState.MODE_SELECT) }
    var isDirectSpeakOverlayOpen by remember { mutableStateOf(false) }
    var isSendingMessage by remember { mutableStateOf(false) }
    var isAiCorrectionSending by remember { mutableStateOf(false) }
    var aiCorrectionOverlayState by remember { mutableStateOf(AiCorrectionOverlayState.RECORDING) }
    var aiCorrectionProbeWavFile by remember { mutableStateOf<File?>(null) }
    var isAiCorrectionTranscribing by remember { mutableStateOf(false) }
    val textModeLastMyBubble by viewModel.textModeLastMyBubble.collectAsState()
    val textModeLastRemoteBubble by viewModel.textModeLastRemoteBubble.collectAsState()
    val remoteAudioLevel by webRtcManager.remoteAudioLevel.collectAsState()
    val aiCorrectionDraftText by viewModel.aiCorrectionDraftText.collectAsState()
    val isAiCorrectionProcessing by viewModel.isAiCorrectionProcessing.collectAsState()
    val aiSuggestionTop1 by viewModel.aiSuggestionTop1.collectAsState()
    val aiSuggestionTop2 by viewModel.aiSuggestionTop2.collectAsState()
    val isRefreshingAiSuggestions by viewModel.isRefreshingAiSuggestions.collectAsState()
    val silenceIntervention by viewModel.silenceIntervention.collectAsState()
    val aiCorrectionAlert by viewModel.aiCorrectionAlert.collectAsState()
    val isAiCorrectionMode = selectedMode == CallMode.AI_CORRECTION
    val isVoiceConversationMode = selectedMode == CallMode.DIRECT || selectedMode == CallMode.AI_CORRECTION
    val isKeypadActive = callScreenState == CallScreenState.KEYPAD
    val shouldAvoidIme = callScreenState == CallScreenState.MODE_SELECT &&
            isVoiceConversationMode &&
            connectionState == WebRtcConnectionState.IN_CALL
    val coroutineScope = rememberCoroutineScope()
    var messageTts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    var isSpeakerphoneOn by remember { mutableStateOf(audioManager.isSpeakerphoneOn) }
    // 음성 클론 TTS 분기용 상태
    val voiceId = remember { org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneStore.getVoiceId(context) }
    val isVoiceCloneEnabled = remember { org.duckdns.dorandoran.callaiassistant.SettingsStore.isVoiceCloneEnabled(context) }
    val introPromptEnabled = SettingsStore.isCallIntroPromptEnabled(context)
    val introPromptStyle = SettingsStore.getCallIntroPromptStyle(context)
    val introPromptCustom = SettingsStore.getCallIntroPromptCustom(context)
    val introPromptText = when (introPromptStyle) {
        "custom" -> introPromptCustom.ifBlank { "안녕하세요. 문자로 입력한 내용을 음성으로 안내해 드릴게요." }
        "basic" -> "안녕하세요, 원활한 소통을 위해 AI 음성 변환 서비스를 이용중입니다. 제 말이 조금 늦더라도 양해 부탁드립니다."
        "situation" -> "안녕하세요. 청각/언어의 어려움으로 텍스트를 음성으로 변환하여 대화하고 있습니다. 천천히 말씀해 주시면 감사하겠습니다."
        "assistant" -> "안녕하세요. 지금은 AI 통화 비서가 대화를 돕고 있습니다. 문자로 입력한 내용을 음성으로 전달해 드릴게요."
        else -> "안녕하세요, 원활한 소통을 위해 AI 음성 변환 서비스를 이용중입니다. 제 말이 조금 늦더라도 양해 부탁드립니다."
    }
    val currentSuggestions = listOf(aiSuggestionTop1, aiSuggestionTop2)
    val displayNumber = if (phoneNumber.isNotBlank()) formatPhoneNumber(phoneNumber) else "상대방"
    val lastRemoteTypedMessageText = textModeLastRemoteBubble.ifBlank { "상대방 대화가 없습니다" }
    val directSpeakPlaceholderText = "직접 말하거나\n위의 추천 답변을 선택하세요"
    val textScale = remember { SettingsStore.getCallTextScale(context) }
    val settingPhoneNumber = remember {
        SettingsStore.getMyPhoneNumber(context).ifBlank { SettingsStore.DEFAULT_MY_PHONE_NUMBER }
    }
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF0E0F13) else Color(0xFFF8FBFF)
    val cardColor = if (isDark) Color(0xFF1B1D24) else Color(0xFFFFFFFF)
    val secondaryTextColor = if (isDark) Color(0xFFA5A8B0) else Color(0xFF7B8089)
    val primaryBlue = Color(0xFF3395FF)
    val controlIconColor = if (isDark) Color(0xFFE8EAF0) else Color(0xFF5D5E62)
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }

    fun syncSpeakerphoneUiState() {
        isSpeakerphoneOn = audioManager.isSpeakerphoneOn
    }

    fun clearAiCorrectionProbeWav() {
        aiCorrectionProbeWavFile?.let { file ->
            if (file.exists() && !file.delete()) {
                file.deleteOnExit()
            }
        }
        aiCorrectionProbeWavFile = null
    }

    fun prepareAiCorrectionProbeWav() {
        clearAiCorrectionProbeWav()
        aiCorrectionProbeWavFile = try {
            TempWavFileFactory.createToneWav(context.cacheDir).also {
                Log.d("WebRtcInCallScreen", "AI correction probe wav prepared: ${it.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w("WebRtcInCallScreen", "AI correction probe wav prepare failed: ${e.message}")
            null
        }
    }

    fun openSpeakOverlay() {
        isDirectSpeakOverlayOpen = true
        if (isAiCorrectionMode) {
            aiCorrectionOverlayState = AiCorrectionOverlayState.RECORDING
            isAiCorrectionSending = false
            viewModel.clearAiCorrectionDraft()
            viewModel.startAiCorrectionRecording()
            onLocalAudioTransmissionToggle(false)
            prepareAiCorrectionProbeWav()
        }
    }

    fun closeSpeakOverlay(restoreDirectMode: Boolean = false) {
        isDirectSpeakOverlayOpen = false
        val wasAiCorrectionMode = selectedMode == CallMode.AI_CORRECTION
        if (wasAiCorrectionMode) {
            viewModel.stopAiCorrectionRecording()
            onLocalAudioTransmissionToggle(true)
        }
        if (restoreDirectMode && wasAiCorrectionMode) {
            selectedMode = CallMode.DIRECT
        }
    }

    fun enterAiCorrectionOverlayFresh() {
        selectedMode = CallMode.AI_CORRECTION
        isDirectSpeakOverlayOpen = true
        aiCorrectionOverlayState = AiCorrectionOverlayState.RECORDING
        isAiCorrectionSending = false
        viewModel.stopAiCorrectionRecording()
        viewModel.clearAiCorrectionDraft()
        viewModel.startAiCorrectionRecording()
        onLocalAudioTransmissionToggle(false)
        prepareAiCorrectionProbeWav()
    }

    fun speakTextWithTts(
        textToSend: String,
        sourceType: String = "ai_response",
        onDone: () -> Unit
    ) {
        android.util.Log.e("VoiceCloneTTS", "[WebRtcInCallScreen] tts 분기: isVoiceCloneEnabled=$isVoiceCloneEnabled, voiceId=$voiceId, text=$textToSend")

        if (isVoiceCloneEnabled && !voiceId.isNullOrBlank()) {
            android.util.Log.d("VoiceCloneTTS", "[WebRtcInCallScreen] ==> voiceCloneTTS 분기 진입, text: $textToSend")
            val callIdStr = "call_${System.currentTimeMillis()}"
            val contextSafe = context.applicationContext
            val audioManagerSafe = audioManager
            coroutineScope.launch {
                val wavFile = VoiceCloneTtsApi.synthesizeVoiceClone(
                    callId = callIdStr,
                    text = textToSend,
                    voiceId = voiceId,
                    sourceType = sourceType,
                    context = contextSafe
                )
                if (wavFile != null && wavFile.exists()) {
                    android.util.Log.d("VoiceCloneTTS", "[WebRtcInCallScreen] 음성 클론 TTS 합성 및 재생 성공: ${wavFile.absolutePath}")
                    SherpaOnnxTtsManager.playWavFile(wavFile, audioManagerSafe)
                    onDone()
                } else {
                    android.util.Log.w("VoiceCloneTTS", "[WebRtcInCallScreen] 음성 클론 TTS 합성 실패, 내장 TTS로 대체: $textToSend")
                    messageTts = TtsManager.initializeForCall(
                        context = contextSafe,
                        onReady = { tts ->
                            messageTts = tts
                            TtsManager.speak(
                                tts = tts,
                                text = textToSend,
                                audioManager = audioManagerSafe,
                                onDone = onDone
                            )
                        }
                    )
                }
            }
        } else {
            android.util.Log.d("VoiceCloneTTS", "[WebRtcInCallScreen] ==> SherpaOnnxTtsManager(내장 TTS) 분기 진입, text: $textToSend")
            messageTts = TtsManager.initializeForCall(
                context = context,
                onReady = { tts ->
                    messageTts = tts
                    TtsManager.speak(
                        tts = tts,
                        text = textToSend,
                        audioManager = audioManager,
                        onDone = onDone
                    )
                }
            )
        }
    }

    fun sendDirectSuggestionNow(text: String) {
        val textToSend = text.trim()
        if (textToSend.isBlank() || isSendingMessage || isRefreshingAiSuggestions) return
        isSendingMessage = true
        onSendAiSuggestion(textToSend)
        onDirectMessageSent(textToSend)
        speakTextWithTts(textToSend) {
            isSendingMessage = false
        }
    }

    LaunchedEffect(Unit) {
        syncSpeakerphoneUiState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncSpeakerphoneUiState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 통화가 완전히 끝났을 때만 TTS 정리 및 안내 멘트 재생
    // 안내 멘트는 최초 통화 시작 시에만 재생, 직접 말하기 모드 복귀 시에는 재생하지 않음
    LaunchedEffect(hasPlayedIntroPrompt, connectionState) {
        if (connectionState == WebRtcConnectionState.DISCONNECTED) {
            Log.d("WebRtcInCallScreen", "Connection state DISCONNECTED - shutting down TTS")
            TtsManager.shutdown(messageTts)
            messageTts = null
            SherpaOnnxTtsManager.shutdown()
            viewModel.resetIntroPromptPlayed()
        } else if (
            connectionState == WebRtcConnectionState.IN_CALL &&
            !hasPlayedIntroPrompt &&
            introPromptEnabled &&
            enableIntroPromptPlayback
        ) {
            viewModel.markIntroPromptPlayed()
            // 통화 시작 안내 멘트도 내 발화 말풍선으로 기록
            if (introPromptText.isNotBlank()) {
                viewModel.sendMessage(introPromptText)
            }
            // 안내멘트 TTS 분기 로그
            android.util.Log.e("VoiceCloneTTS", "[WebRtcInCallScreen] 안내멘트 tts 분기: isVoiceCloneEnabled=$isVoiceCloneEnabled, voiceId=$voiceId, text=$introPromptText")
            if (isVoiceCloneEnabled && !voiceId.isNullOrBlank()) {
                // 음성 클론 TTS 분기
                android.util.Log.d("VoiceCloneTTS", "[WebRtcInCallScreen] ==> 안내멘트 voiceCloneTTS 분기 진입, text: $introPromptText")
                val callIdStr = "call_${System.currentTimeMillis()}"
                val contextSafe = context.applicationContext
                val audioManagerSafe = audioManager
                coroutineScope.launch {
                    val wavFile = VoiceCloneTtsApi.synthesizeVoiceClone(
                        callId = callIdStr,
                        text = introPromptText,
                        voiceId = voiceId,
                        // sourceType = "intro_prompt",
                        sourceType = "ai_response",
                        context = contextSafe
                    )
                    if (wavFile != null && wavFile.exists()) {
                        android.util.Log.d("VoiceCloneTTS", "[WebRtcInCallScreen] 안내멘트 음성 클론 TTS 합성 및 재생 성공: ${wavFile.absolutePath}")
                        SherpaOnnxTtsManager.playWavFile(wavFile, audioManagerSafe)
                    } else {
                        android.util.Log.w("VoiceCloneTTS", "[WebRtcInCallScreen] 안내멘트 음성 클론 TTS 합성 실패, 내장 TTS로 대체: $introPromptText")
                        messageTts = TtsManager.initializeForCall(
                            context = contextSafe,
                            onReady = { tts ->
                                messageTts = tts
                                TtsManager.speak(
                                    tts = tts,
                                    text = introPromptText,
                                    audioManager = audioManagerSafe,
                                )
                            }
                        )
                    }
                }
            } else {
                // 내장 TTS 분기
                android.util.Log.d("VoiceCloneTTS", "[WebRtcInCallScreen] ==> 안내멘트 SherpaOnnxTtsManager(내장 TTS) 분기 진입, text: $introPromptText")
                messageTts = TtsManager.initializeForCall(
                    context = context,
                    onReady = { tts ->
                        messageTts = tts
                        TtsManager.speak(
                            tts = tts,
                            text = introPromptText,
                            audioManager = audioManager
                        )
                    }
                )
            }
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState != WebRtcConnectionState.IN_CALL && isDirectSpeakOverlayOpen) {
            closeSpeakOverlay()
        }
    }

    LaunchedEffect(aiCorrectionAlert) {
        if (aiCorrectionAlert) {
            selectedMode = CallMode.AI_CORRECTION
            viewModel.consumeComprehensionAlert()
        }
    }

    LaunchedEffect(silenceIntervention.eventId) {
        if (!silenceIntervention.visible || silenceIntervention.eventId <= 0L) return@LaunchedEffect
        val text = silenceIntervention.interventionText.ifBlank { "잠시만요" }
        try {
            viewModel.sendMessage(
                text,
                origin = org.duckdns.dorandoran.callaiassistant.ui.viewmodel.MessageOrigin.TEXT_MODE
            )
            speakTextWithTts(
                textToSend = text,
                sourceType = "crisis_intervention"
            ) { }
            delay(8000)
        } finally {
            // Ensure stale intervention UI does not remain when this effect is cancelled
            // (e.g., screen navigation / recomposition lifecycle changes).
            viewModel.dismissSilenceIntervention()
        }
    }

    LaunchedEffect(selectedMode) {
        if (selectedMode != CallMode.AI_CORRECTION) {
            viewModel.stopAiCorrectionRecording()
            aiCorrectionOverlayState = AiCorrectionOverlayState.RECORDING
            isAiCorrectionSending = false
            isAiCorrectionTranscribing = false
            clearAiCorrectionProbeWav()
            if (isDirectSpeakOverlayOpen) {
                isDirectSpeakOverlayOpen = false
            }
            onLocalAudioTransmissionToggle(true)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAiCorrectionRecording()
            clearAiCorrectionProbeWav()
            onLocalAudioTransmissionToggle(true)
            viewModel.dismissSilenceIntervention()
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
                        WebRtcConnectionState.CONNECTED -> "T.mate 연결 중"
                        WebRtcConnectionState.IN_CALL -> "통화 시간 ${formatDuration(callDurationSeconds)}"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp * textScale,
                        fontWeight = FontWeight.Medium
                    ),
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    RemoteVoiceWaveMini(
                        level = remoteAudioLevel,
                        modifier = Modifier.size(width = 20.dp, height = 18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayNumber,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 30.sp * textScale,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formatPhoneNumber(phoneNumber),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp * textScale
                    ),
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

            }

            when (callScreenState) {
                CallScreenState.MODE_SELECT -> {
                    if (isVoiceConversationMode && connectionState == WebRtcConnectionState.IN_CALL) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (silenceIntervention.visible) {
                                CrisisInterventionPanel(
                                    remoteText = lastRemoteTypedMessageText,
                                    interventionText = silenceIntervention.interventionText.ifBlank { "잠시만요" },
                                    textScale = textScale
                                )
                            } else {
                                Text(
                                    text = lastRemoteTypedMessageText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * textScale * 1.4f).sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
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
                    .then(if (shouldAvoidIme) Modifier.imePadding() else Modifier)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(topStart = 43.dp, topEnd = 43.dp),
                        ambientColor = Color.Black.copy(alpha = 0.15f),
                        spotColor = Color.Black.copy(alpha = 0.18f),
                        clip = false
                    )
                    .clip(RoundedCornerShape(topStart = 43.dp, topEnd = 43.dp))
                    .background(cardColor)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (callScreenState == CallScreenState.MODE_SELECT) {
                        if (connectionState == WebRtcConnectionState.IN_CALL && isVoiceConversationMode) {
                            if (isAiCorrectionMode && isDirectSpeakOverlayOpen) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = { closeSpeakOverlay(restoreDirectMode = true) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "닫기",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFFFFFFFF))
                                        .border(
                                            width = 1.dp,
                                            color = Color(0xFFEAEAEA),
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.lucide_mic),
                                            contentDescription = "마이크",
                                            tint = Color(0xFF5D5D5D),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (aiCorrectionOverlayState != AiCorrectionOverlayState.RECORDING) {
                                                "보정된 문장을 확인해주세요"
                                            } else {
                                                "지금 이렇게 말하고 있어요"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color(0xFF5D5D5D)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = aiCorrectionDraftText.ifBlank { "말씀하시면 문장이 여기에 표시됩니다" },
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale * 1.4f,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                when (aiCorrectionOverlayState) {
                                    AiCorrectionOverlayState.RECORDING -> {
                                        val startCorrectionEnabled =
                                            aiCorrectionDraftText.isNotBlank() &&
                                                !isAiCorrectionProcessing &&
                                                !isAiCorrectionTranscribing
                                        val startCorrectionButtonBrush = if (startCorrectionEnabled) {
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0xFF9A7BFF),
                                                    Color(0xFF87B7FF),
                                                    Color(0xFF6FE0C8)
                                                )
                                            )
                                        } else {
                                            val disabled = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                                            Brush.horizontalGradient(colors = listOf(disabled, disabled))
                                        }

                                        Button(
                                            onClick = {
                                                val rawText = aiCorrectionDraftText
                                                if (rawText.isBlank() || isAiCorrectionProcessing || isAiCorrectionTranscribing) return@Button
                                                viewModel.stopAiCorrectionRecording()
                                                isAiCorrectionTranscribing = true
                                                coroutineScope.launch {
                                                    val probeText = try {
                                                        val probeFile = aiCorrectionProbeWavFile
                                                        if (probeFile != null && probeFile.exists()) {
                                                            RemoteSttApi.recognize(probeFile).orEmpty()
                                                        } else {
                                                            ""
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.w("WebRtcInCallScreen", "Whisper transcribe failed: ${e.message}")
                                                        ""
                                                    } finally {
                                                        clearAiCorrectionProbeWav()
                                                    }
                                                    val textForCorrection = probeText.trim().ifBlank { rawText.trim() }
                                                    viewModel.requestAiCorrection(
                                                        rawText = textForCorrection,
                                                        phoneNumber = settingPhoneNumber
                                                    ) { _ ->
                                                        isAiCorrectionTranscribing = false
                                                        aiCorrectionOverlayState = AiCorrectionOverlayState.READY_TO_SEND
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(startCorrectionButtonBrush),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = Color.White,
                                                disabledContainerColor = Color.Transparent,
                                                disabledContentColor = Color.White
                                            ),
                                            enabled = startCorrectionEnabled
                                        ) {
                                            Text(
                                                when {
                                                    isAiCorrectionTranscribing -> "음성 분석 중..."
                                                    isAiCorrectionProcessing -> "AI 보정 중..."
                                                    else -> "보정 시작"
                                                }
                                            )
                                        }
                                    }
                                    AiCorrectionOverlayState.READY_TO_SEND -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    onLocalAudioTransmissionToggle(false)
                                                    viewModel.clearAiCorrectionDraft()
                                                    viewModel.startAiCorrectionRecording()
                                                    isAiCorrectionTranscribing = false
                                                    prepareAiCorrectionProbeWav()
                                                    aiCorrectionOverlayState = AiCorrectionOverlayState.RECORDING
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("다시 말하기")
                                            }
                                            Button(
                                                onClick = {
                                                    val textToSend = aiCorrectionDraftText.trim()
                                                    if (textToSend.isBlank() || isAiCorrectionSending) return@Button
                                                    isAiCorrectionSending = true
                                                    onLocalAudioTransmissionToggle(true)
                                                    viewModel.sendMessage(
                                                        textToSend,
                                                        origin = org.duckdns.dorandoran.callaiassistant.ui.viewmodel.MessageOrigin.TEXT_MODE
                                                    )
                                                    speakTextWithTts(textToSend) {
                                                        isAiCorrectionSending = false
                                                        aiCorrectionOverlayState = AiCorrectionOverlayState.SENT
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = primaryBlue,
                                                    contentColor = Color.White
                                                ),
                                                enabled = aiCorrectionDraftText.isNotBlank() && !isAiCorrectionSending
                                            ) {
                                                Text(if (isAiCorrectionSending) "전송 중..." else "보내기")
                                            }
                                        }
                                    }
                                    AiCorrectionOverlayState.SENT -> {
                                        OutlinedButton(
                                            onClick = {
                                                onLocalAudioTransmissionToggle(false)
                                                viewModel.clearAiCorrectionDraft()
                                                viewModel.startAiCorrectionRecording()
                                                isAiCorrectionTranscribing = false
                                                prepareAiCorrectionProbeWav()
                                                aiCorrectionOverlayState = AiCorrectionOverlayState.RECORDING
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("다시 말하기")
                                        }
                                    }
                                }
                            } else {
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
                                            imageVector = Icons.Default.Lightbulb,
                                            contentDescription = "AI 추천",
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "AI 추천 답변",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    IconButton(
                                        enabled = !isRefreshingAiSuggestions,
                                        onClick = {
                                            viewModel.refreshAiSuggestions()
                                        }
                                    ) {
                                        if (!isRefreshingAiSuggestions) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "추천 새로고침",
                                                tint = secondaryTextColor
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val suggestionBorderBrush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xCCA371FE),
                                            Color(0xCC74A5FA)
                                        )
                                    )
                                    currentSuggestions.forEach { suggestion ->
                                        OutlinedButton(
                                            onClick = {
                                                if (isRefreshingAiSuggestions || suggestion.isBlank()) return@OutlinedButton
                                                sendDirectSuggestionNow(suggestion)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            enabled = !isRefreshingAiSuggestions && suggestion.isNotBlank(),
                                            border = BorderStroke(
                                                width = 2.dp,
                                                brush = suggestionBorderBrush
                                            ),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isRefreshingAiSuggestions) {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                                } else {
                                                    Color.Transparent
                                                },
                                                contentColor = if (isRefreshingAiSuggestions) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onBackground
                                                },
                                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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

                                Spacer(modifier = Modifier.height(24.dp))

                                if (textModeLastMyBubble.isNotBlank() && !isAiCorrectionMode) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(126.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalArrangement = Arrangement.Top
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.lucide_mic),
                                                contentDescription = "마이크",
                                                tint = Color(0xFF5D5D5D),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "지금 이렇게 말하고 있어요",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = textModeLastMyBubble,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {},
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(126.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        enabled = false,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            disabledContentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(0.dp, Color.Transparent)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.lucide_mic),
                                                contentDescription = "마이크",
                                                tint = Color(0xFF5D5D5D),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = directSpeakPlaceholderText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = secondaryTextColor,
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "전화 모드를 선택해주세요",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 13.sp
                                ),
                                color = Color(0xFF5D5D5D),
                                modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
                                textAlign = TextAlign.Start
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (!isAiCorrectionMode) {
                            // 모드 선택 버튼들
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Button(
                                    onClick = { selectedMode = CallMode.DIRECT },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedMode == CallMode.DIRECT) primaryBlue else Color.White,
                                        contentColor = if (selectedMode == CallMode.DIRECT) Color.White else Color.Black
                                    ),
                                    border = if (selectedMode == CallMode.DIRECT) null else BorderStroke(
                                        width = 1.dp,
                                        color = if (isDark) Color(0xFF3C3F48) else Color(0xFFEAEAEA)
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text("직접 말하기")
                                }

                                Button(
                                    onClick = {
                                        enterAiCorrectionOverlayFresh()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedMode == CallMode.AI_CORRECTION) primaryBlue else Color.White,
                                        contentColor = if (selectedMode == CallMode.AI_CORRECTION) Color.White else Color.Black
                                    ),
                                    border = if (selectedMode == CallMode.AI_CORRECTION) null else BorderStroke(
                                        width = 1.dp,
                                        color = if (isDark) Color(0xFF3C3F48) else Color(0xFFEAEAEA)
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text("AI 보정")
                                }

                                Button(
                                    onClick = {
                                        selectedMode = CallMode.TEXT
                                        navController?.navigate("call_typing")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedMode == CallMode.TEXT) primaryBlue else Color.White,
                                        contentColor = if (selectedMode == CallMode.TEXT) Color.White else Color.Black
                                    ),
                                    border = if (selectedMode == CallMode.TEXT) null else BorderStroke(
                                        width = 1.dp,
                                        color = if (isDark) Color(0xFF3C3F48) else Color(0xFFEAEAEA)
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
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .clickable {
                                    isSpeakerphoneOn = !isSpeakerphoneOn
                                    onSpeakerphoneToggle(isSpeakerphoneOn)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_speaker_fill),
                                contentDescription = "스피커",
                                tint = if (isSpeakerphoneOn) primaryBlue else controlIconColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "스피커",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF5E5E5E)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF6B6B))
                                .clickable(onClick = onEndCall),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_call_end_solar),
                                contentDescription = "통화 종료",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(39.2.dp)
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(33.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .clickable {
                                    callScreenState = if (callScreenState == CallScreenState.KEYPAD) {
                                        CallScreenState.MODE_SELECT
                                    } else {
                                        CallScreenState.KEYPAD
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_keypad_fill),
                                contentDescription = "숫자 키패드",
                                tint = if (isKeypadActive) primaryBlue else controlIconColor,
                                modifier = Modifier.size(33.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "숫자 키패드",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF5E5E5E)
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

private enum class AiCorrectionOverlayState {
    RECORDING,
    READY_TO_SEND,
    SENT
}

@Composable
private fun CrisisInterventionPanel(
    remoteText: String,
    interventionText: String,
    textScale: Float
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = remoteText.ifBlank { "네, 무엇을 도와드릴까요?" },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * textScale * 1.4f).sp
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Surface(
            color = Color(0xFFEEF5FF),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shining_ai),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "AI가 \"$interventionText\"를 대신 전달했습니다",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = Color(0xFF73777F)
                )
            }
        }
    }
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
        val context = LocalContext.current
        val webRtcManager = remember { WebRtcManager(context.applicationContext) }
        WebRtcInCallScreen(
            phoneNumber = "01012345678",
            connectionState = WebRtcConnectionState.CONNECTED,
            callDurationSeconds = 0,
            webRtcManager = webRtcManager,
            onEndCall = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WebRtcInCallScreenInCallPreview() {
    CallaiassistantTheme {
        val context = LocalContext.current
        val webRtcManager = remember { WebRtcManager(context.applicationContext) }
        WebRtcInCallScreen(
            phoneNumber = "01012345678",
            connectionState = WebRtcConnectionState.IN_CALL,
            callDurationSeconds = 125,
            webRtcManager = webRtcManager,
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

