package org.duckdns.dorandoran.callaiassistant

import android.media.AudioManager
import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import org.duckdns.dorandoran.callaiassistant.data.CallLogRepository
import org.duckdns.dorandoran.callaiassistant.tts.TtsManager
import org.duckdns.dorandoran.callaiassistant.CallAudioHelper
import org.duckdns.dorandoran.callaiassistant.InCallManager
import org.duckdns.dorandoran.callaiassistant.ui.screens.CallState
import org.duckdns.dorandoran.callaiassistant.ui.screens.InCallScreen
import org.duckdns.dorandoran.callaiassistant.ui.screens.WebRtcInCallScreen
import org.duckdns.dorandoran.callaiassistant.webrtc.CallAudioManager
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme
import org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcManager

class InCallActivity : ComponentActivity() {
    companion object {
        @Volatile
        var isVisible: Boolean = false
    }
    // 다이얼 화면에서 시작되었는지 추적 (통화 종료 후 다이얼 화면 복귀 여부 결정)
    private var isFromDialer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 잠금 화면 위에 표시 및 화면 켜기 설정 (API별 호환 처리, 권한 필요 없음)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        // 추가: 화면 끄기 방지 (선택사항)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 다이얼 화면에서 시작되었는지 확인
        isFromDialer = intent?.getBooleanExtra("from_dialer", false) ?: false
        
        // 인텐트로 전달된 수신 전화 정보를 시그널링 매니저에 설정
        if (intent?.action == CallListeningService.ACTION_INCOMING_CALL) {
            val callId = intent.getStringExtra(CallListeningService.EXTRA_CALL_ID) ?: ""
            val roomId = intent.getStringExtra(CallListeningService.EXTRA_ROOM_ID)
                ?: org.duckdns.dorandoran.callaiassistant.webrtc.WEBRTC_ROOM_ID
            if (callId.isNotEmpty()) {
                (application as? CallApp)?.callSignalingManager?.setIncomingFromIntent(
                    org.duckdns.dorandoran.callaiassistant.webrtc.CallSignalingManager.IncomingCallInfo(callId, roomId)
                )
            }
        }
        enableEdgeToEdge()

        // 전체화면: 소프트키(네비게이션 바) 숨기기
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 뒤로가기 비활성화 (통화 중 실수로 종료 방지)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 아무 동작 안 함 - 뒤로가기 무시
            }
        })

        setContent {
            CallaiassistantTheme {
                val callSignalingManager = (application as? CallApp)?.callSignalingManager
                val callAudioManager = remember { CallAudioManager(applicationContext) }
                val webRtcManager = remember {
                    callSignalingManager?.let { WebRtcManager(applicationContext, it) }
                }
                var isAccepting by remember { mutableStateOf(false) }
                var isAccepted by remember { mutableStateOf(false) }
                var wasBackgroundLaunched by remember { mutableStateOf(false) }
                val incomingCallState = callSignalingManager?.incomingCall
                val incomingCall by incomingCallState?.collectAsState() ?: remember { mutableStateOf(null) }
                
                // 백그라운드에서 full-screen intent로 띄워진 경우, 자동으로 수락 처리
                LaunchedEffect(incomingCall, wasBackgroundLaunched) {
                    if (intent?.action == CallListeningService.ACTION_INCOMING_CALL && 
                        incomingCall != null && 
                        !wasBackgroundLaunched && 
                        !isAccepted) {
                        wasBackgroundLaunched = true
                        // 약간의 지연 후 자동 수락
                        delay(500)
                        val callId = incomingCall!!.callId
                        isAccepting = true
                        isAccepted = true
                        callAudioManager.start()
                        callSignalingManager?.acceptCall(callId)
                        webRtcManager?.joinAsCallee(callId)
                    }
                }

                // 원격에서 hangup을 받아 incomingCall이 null이 된 경우 액티비티 종료
                LaunchedEffect(incomingCall, isAccepting, isAccepted) {
                    if (incomingCall == null && !isAccepted) {
                        if (isAccepting) {
                            delay(1500)
                            val hasCall = InCallManager.getPrimaryCall() != null
                            isAccepting = false
                            if (!hasCall) {
                                // 수락 실패 시 종료
                                finish()
                            }
                        } else if (InCallManager.getPrimaryCall() == null) {
                            // 수신 알림 상태에서 incomingCall이 null이고 실제 통화도 없으면 종료
                            finish()
                        }
                    }
                }

                if (incomingCall != null && !isAccepted) {
                    val info = incomingCall!!
                    org.duckdns.dorandoran.callaiassistant.ui.screens.IncomingCallScreen(
                        callerName = "상대방",
                        onAccept = {
                            startService(Intent(this@InCallActivity, CallListeningService::class.java).apply {
                                action = CallListeningService.ACTION_CALL_HANDLED
                            })
                            // InCallActivity에서 바로 전화 받기 (MainActivity로 이동하지 않음)
                            callSignalingManager?.acceptCall(info.callId)
                            isAccepting = true
                            isAccepted = true
                            callAudioManager.start()
                            webRtcManager?.joinAsCallee(info.callId)
                        },
                        onReject = {
                            startService(Intent(this@InCallActivity, CallListeningService::class.java).apply {
                                action = CallListeningService.ACTION_CALL_HANDLED
                            })
                            // 거절 전송 후 종료
                            info.callId.let { id -> callSignalingManager?.rejectCall(id) }
                            finish()
                        }
                    )
                } else if (isAccepted) {
                    WebRtcCallContent(
                        webRtcManager = webRtcManager,
                        callAudioManager = callAudioManager,
                        onEndCall = {
                            callAudioManager.stop()
                            webRtcManager?.hangup()
                            callSignalingManager?.clearIncoming()
                            callSignalingManager?.clearCallerMode()
                            callSignalingManager?.startListening()
                            startCallListeningService()
                            if (isFromDialer) {
                                val intent = Intent(this@InCallActivity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                startActivity(intent)
                            }
                            finish()
                        },
                        onRemoteDisconnected = {
                            callAudioManager.stop()
                            webRtcManager?.hangup()
                            callSignalingManager?.clearIncoming()
                            callSignalingManager?.clearCallerMode()
                            callSignalingManager?.startListening()
                            startCallListeningService()
                            if (isFromDialer) {
                                val intent = Intent(this@InCallActivity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                startActivity(intent)
                            }
                            finish()
                        }
                    )
                } else {
                    InCallContent(
                        onFinish = { 
                            // 다이얼 화면에서 시작된 경우만 다이얼 화면으로 복귀
                            if (isFromDialer) {
                                val intent = Intent(this@InCallActivity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                startActivity(intent)
                            }
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // 복귀 시에도 소프트키 숨김 유지
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isVisible = true
    }

    override fun onStop() {
        super.onStop()
        isVisible = false
    }

    private fun startCallListeningService() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, CallListeningService::class.java))
        } else {
            startService(Intent(this, CallListeningService::class.java))
        }
    }
}

@Composable
private fun WebRtcCallContent(
    webRtcManager: WebRtcManager?,
    callAudioManager: CallAudioManager,
    onEndCall: () -> Unit,
    onRemoteDisconnected: () -> Unit
) {
    val manager = webRtcManager ?: run {
        LaunchedEffect(Unit) { onEndCall() }
        return
    }
    val connectionState by manager.connectionState.collectAsState()
    var callDuration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        manager.onRemoteDisconnected = { onRemoteDisconnected() }
    }

    LaunchedEffect(connectionState) {
        if (connectionState == org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState.IN_CALL) {
            while (true) {
                delay(1000)
                callDuration += 1
            }
        } else {
            callDuration = 0L
        }
    }

    val logMessages by manager.logMessages.collectAsState()
    WebRtcInCallScreen(
        phoneNumber = "상대방",
        connectionState = connectionState,
        callDurationSeconds = callDuration,
        logMessages = logMessages,
        onEndCall = onEndCall,
        onSpeakerphoneToggle = { isOn ->
            callAudioManager.setSpeakerphone(isOn)
        }
    )
}

@Composable
private fun InCallContent(onFinish: () -> Unit) {
    val call = remember { InCallManager.getPrimaryCall() }
    var contactName by remember { mutableStateOf<String?>(null) }
    var callDuration by remember { mutableLongStateOf(0L) }
    var callState by remember { mutableStateOf(CallState.DIALING) }
    var callStartTime by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val repository = remember { CallLogRepository(context) }
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    val isSpeakerOn by InCallManager.speakerState

    if (call == null) {
        LaunchedEffect(Unit) { onFinish() }
        return
    }

    val number = InCallManager.getCallNumber(call)

    LaunchedEffect(number) {
        contactName = repository.getContactName(number)
    }

    DisposableEffect(call) {
        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                callState = when (state) {
                    Call.STATE_DIALING -> CallState.DIALING
                    Call.STATE_RINGING -> CallState.RINGING
                    Call.STATE_ACTIVE -> {
                        callStartTime = System.currentTimeMillis()
                        CallState.ACTIVE
                    }
                    Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                        onFinish()
                        CallState.ENDED
                    }
                    else -> CallState.DIALING
                }
            }
        }
        call.registerCallback(callback)
        callState = when (call.state) {
            Call.STATE_RINGING -> CallState.RINGING
            Call.STATE_ACTIVE -> {
                callStartTime = System.currentTimeMillis()
                CallState.ACTIVE
            }
            else -> CallState.DIALING
        }
        onDispose { call.unregisterCallback(callback) }
    }

    // 통화 연결 시 오디오 모드 및 TTS 초기화
    LaunchedEffect(callState) {
        if (callState == CallState.ACTIVE) {
            CallAudioHelper.setCallAudioMode(audioManager)
        }
        if (callState == CallState.ACTIVE) {
            if (tts == null) {
                tts = TtsManager.initializeForCall(
                context,
                onReady = { t ->
                    tts = t
                    ttsReady = true
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            TtsManager.shutdown(tts)
            CallAudioHelper.restoreAudioMode(audioManager)
        }
    }

    LaunchedEffect(callState, callStartTime) {
        while (callState == CallState.ACTIVE && callStartTime != null) {
            delay(1000)
            callStartTime?.let { start ->
                callDuration = (System.currentTimeMillis() - start) / 1000
            }
        }
    }

    InCallScreen(
        phoneNumber = number,
        contactName = contactName,
        callState = callState,
        callDurationSeconds = callDuration,
        onAnswerCall = { InCallManager.answer(call) },
        onSpeakText = if (ttsReady && tts != null) {
            { text -> tts?.let { TtsManager.speak(it, text, audioManager) } }
        } else null,
        isSpeakerOn = isSpeakerOn,
        onToggleSpeaker = { InCallManager.setSpeakerphone(!isSpeakerOn) },
        onEndCall = {
            InCallManager.disconnect(call)
            onFinish()
        },
        modifier = Modifier.fillMaxSize()
    )
}
