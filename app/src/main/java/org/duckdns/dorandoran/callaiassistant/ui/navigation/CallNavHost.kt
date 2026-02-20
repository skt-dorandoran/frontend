package org.duckdns.dorandoran.callaiassistant.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.duckdns.dorandoran.callaiassistant.ui.screens.CallTypingScreen
import org.duckdns.dorandoran.callaiassistant.ui.screens.WebRtcInCallScreen
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel
import org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState
import org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcManager

object CallRoutes {
    const val INTRO = "call_intro"
    const val TYPING = "call_typing"
}

@Composable
fun CallNavHost(
    phoneNumber: String,
    connectionState: WebRtcConnectionState,
    callDurationSeconds: Long,
    webRtcManager: WebRtcManager,
    logMessages: List<String> = emptyList(),
    onEndCall: () -> Unit,
    onSpeakerphoneToggle: (Boolean) -> Unit = {},
    onLocalAudioTransmissionToggle: (Boolean) -> Unit = {},
    enableIntroPromptPlayback: Boolean = true,
    navController: NavHostController = rememberNavController(),
    callViewModel: CallViewModel = viewModel()
) {
    var hasActiveConversationSession by remember { mutableStateOf(false) }

    LaunchedEffect(connectionState, phoneNumber) {
        val callActive = connectionState != WebRtcConnectionState.DISCONNECTED
        if (callActive && !hasActiveConversationSession) {
            val normalizedNumber = phoneNumber.ifBlank { "unknown" }
            val sessionKey = "$normalizedNumber-${System.currentTimeMillis()}"
            callViewModel.startNewConversationSession(sessionKey)
            hasActiveConversationSession = true
        } else if (!callActive && hasActiveConversationSession) {
            callViewModel.endConversationSession()
            hasActiveConversationSession = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (hasActiveConversationSession) {
                callViewModel.endConversationSession()
                hasActiveConversationSession = false
            }
        }
    }
    
    // connectionState에 따라 callTime 설정
    val callTimeString = when (connectionState) {
        WebRtcConnectionState.CONNECTED -> "연결 중..."
        WebRtcConnectionState.IN_CALL -> {
            // 통화 중일 때만 시간 표시  
            val minutes = callDurationSeconds / 60
            val seconds = callDurationSeconds % 60
            String.format("%02d:%02d", minutes, seconds)
        }
        WebRtcConnectionState.DISCONNECTED -> ""
    }
    
    // ViewModel에 통화 정보 업데이트
    callViewModel.updateCallInfo(
        phoneNumber = if (phoneNumber.isNotBlank()) formatPhoneNumber(phoneNumber) else "상대방",
        hospitalName = if (phoneNumber.isNotBlank()) formatPhoneNumber(phoneNumber) else "상대방",
        callTime = callTimeString
    )

    NavHost(
        navController = navController,
        startDestination = CallRoutes.INTRO
    ) {
        composable(CallRoutes.INTRO) {
            // 통화 중일 때 뒤로가기 버튼 무시
            BackHandler { }
            WebRtcInCallScreen(
                phoneNumber = phoneNumber,
                connectionState = connectionState,
                callDurationSeconds = callDurationSeconds,
                logMessages = logMessages,
                onDirectMessageSent = { message ->
                    callViewModel.sendMessage(message)
                },
                onEndCall = onEndCall,
                onSpeakerphoneToggle = onSpeakerphoneToggle,
                onLocalAudioTransmissionToggle = onLocalAudioTransmissionToggle,
                enableIntroPromptPlayback = enableIntroPromptPlayback,
                navController = navController,
                viewModel = callViewModel
            )
        }

        composable(CallRoutes.TYPING) {
            CallTypingScreen(
                navController = navController,
                viewModel = callViewModel,
                webRtcManager = webRtcManager,
                onEndCall = onEndCall
            )
        }
    }
}

private fun formatPhoneNumber(phoneNumber: String): String {
    return when {
        phoneNumber.startsWith("02") && phoneNumber.length == 9 ->
            "${phoneNumber.substring(0, 2)}-${phoneNumber.substring(2, 5)}-${phoneNumber.substring(5)}"
        phoneNumber.startsWith("02") && phoneNumber.length == 10 ->
            "${phoneNumber.substring(0, 2)}-${phoneNumber.substring(2, 6)}-${phoneNumber.substring(6)}"
        phoneNumber.length == 10 ->
            "${phoneNumber.substring(0, 3)}-${phoneNumber.substring(3, 6)}-${phoneNumber.substring(6)}"
        phoneNumber.length == 11 ->
            "${phoneNumber.substring(0, 3)}-${phoneNumber.substring(3, 7)}-${phoneNumber.substring(7)}"
        else -> phoneNumber
    }
}
