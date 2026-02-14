package org.duckdns.dorandoran.callaiassistant.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.duckdns.dorandoran.callaiassistant.ui.screens.CallTypingScreen
import org.duckdns.dorandoran.callaiassistant.ui.screens.WebRtcInCallScreen
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel
import org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState

object CallRoutes {
    const val INTRO = "call_intro"
    const val TYPING = "call_typing"
}

@Composable
fun CallNavHost(
    phoneNumber: String,
    connectionState: WebRtcConnectionState,
    callDurationSeconds: Long,
    logMessages: List<String> = emptyList(),
    sttText: String = "",
    aiSuggestions: List<String> = listOf(),
    onSendAiSuggestion: (String) -> Unit = {},
    onEndCall: () -> Unit,
    onSpeakerphoneToggle: (Boolean) -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    val callViewModel: CallViewModel = viewModel()
    
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
        phoneNumber = phoneNumber,
        hospitalName = if (phoneNumber.isNotBlank()) formatPhoneNumber(phoneNumber) else "상대방",
        callTime = callTimeString
    )

    NavHost(
        navController = navController,
        startDestination = CallRoutes.INTRO
    ) {
        composable(CallRoutes.INTRO) {
            // 통화 시작 시 history 초기화
            callViewModel.clearHistory()
            // 통화 중일 때 뒤로가기 버튼 무시
            BackHandler { }
            WebRtcInCallScreen(
                phoneNumber = phoneNumber,
                connectionState = connectionState,
                callDurationSeconds = callDurationSeconds,
                logMessages = logMessages,
                sttText = sttText,
                aiSuggestions = aiSuggestions,
                onSendAiSuggestion = onSendAiSuggestion,
                onDirectMessageSent = { message ->
                    callViewModel.sendMessage(message)
                },
                onEndCall = onEndCall,
                onSpeakerphoneToggle = onSpeakerphoneToggle,
                navController = navController
            )
        }

        composable(CallRoutes.TYPING) {
            // 통화 시작 시 history 초기화
            callViewModel.clearHistory()
            CallTypingScreen(
                navController = navController,
                viewModel = callViewModel,
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
