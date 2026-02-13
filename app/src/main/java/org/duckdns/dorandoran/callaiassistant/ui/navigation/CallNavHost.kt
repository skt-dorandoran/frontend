package org.duckdns.dorandoran.callaiassistant.ui.navigation

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
    
    // callDurationSeconds를 시간 문자열로 변환
    val minutes = callDurationSeconds / 60
    val seconds = callDurationSeconds % 60
    val callTimeString = String.format("%02d:%02d", minutes, seconds)
    
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
            WebRtcInCallScreen(
                phoneNumber = phoneNumber,
                connectionState = connectionState,
                callDurationSeconds = callDurationSeconds,
                logMessages = logMessages,
                sttText = sttText,
                aiSuggestions = aiSuggestions,
                onSendAiSuggestion = onSendAiSuggestion,
                onEndCall = onEndCall,
                onSpeakerphoneToggle = onSpeakerphoneToggle,
                navController = navController
            )
        }
        
        composable(CallRoutes.TYPING) {
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
