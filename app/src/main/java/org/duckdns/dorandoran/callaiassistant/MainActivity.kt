package org.duckdns.dorandoran.callaiassistant

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.duckdns.dorandoran.callaiassistant.ui.screens.CallHistoryScreen
import org.duckdns.dorandoran.callaiassistant.DefaultDialerHelper
import org.duckdns.dorandoran.callaiassistant.ui.screens.DialerScreen
import org.duckdns.dorandoran.callaiassistant.ui.screens.IncomingCallScreen
import org.duckdns.dorandoran.callaiassistant.ui.screens.WebRtcInCallScreen
import org.duckdns.dorandoran.callaiassistant.webrtc.CallAudioManager
import org.duckdns.dorandoran.callaiassistant.webrtc.CallSignalingManager
import org.duckdns.dorandoran.callaiassistant.webrtc.RingbackToneHelper
import org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcManager
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme
import org.duckdns.dorandoran.callaiassistant.tts.TtsManager

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTO_CALL = "extra_auto_call"
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionsState = allGranted
        savePermissionsRequested()
        onboardingPermissionPending = false
        if (!allGranted) {
            showMissingPermissionsWarning = true
            saveMissingPermissionsWarning(true)
            return@registerForActivityResult
        }
        showMissingPermissionsWarning = false
        saveMissingPermissionsWarning(false)
        showAppWithoutDefaultDialer = true
        onboardingCompleted = true
        saveOnboardingCompleted()
    }

    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* 결과는 onResume에서 isDefaultDialer로 확인 */ }

    private var permissionsState by mutableStateOf(false)
    private var showAppWithoutDefaultDialer by mutableStateOf(false)
    private var hasRequestedPermissions by mutableStateOf(false)
    private var onboardingCompleted by mutableStateOf(false)
    private var showMissingPermissionsWarning by mutableStateOf(false)
    private var onboardingPermissionPending by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        checkPermissions()
        hasRequestedPermissions = loadPermissionsRequested()
        onboardingCompleted = loadOnboardingCompleted()
        showMissingPermissionsWarning = loadMissingPermissionsWarning()
        if (permissionsState) {
            showMissingPermissionsWarning = false
            saveMissingPermissionsWarning(false)
            showAppWithoutDefaultDialer = true
        } else if (onboardingCompleted) {
            showMissingPermissionsWarning = true
            saveMissingPermissionsWarning(true)
            showAppWithoutDefaultDialer = false
        }
        
        // 앱 시작 시 CallListeningService 시작 (백그라운드 청취용)
        startCallListeningService()

        val initialPhoneNumber = intent?.data?.takeIf { it.scheme == "tel" }
            ?.schemeSpecificPart?.orEmpty()?.filter { c -> c.isDigit() || c == '+' } ?: ""
        val autoCall = intent?.getBooleanExtra(EXTRA_AUTO_CALL, false) ?: false

        handleIncomingCallIntent(intent)

        setContent {
            CallaiassistantTheme {
                when {
                    showMissingPermissionsWarning -> MissingPermissionsWarningScreen(
                        onOpenSettings = { openAppSettings() },
                        onCloseApp = { finish() }
                    )
                    !onboardingCompleted -> OnboardingFlow(
                        onComplete = {
                            if (onboardingPermissionPending) {
                                return@OnboardingFlow
                            }
                            checkPermissions()
                            if (!permissionsState) {
                                hasRequestedPermissions = true
                                savePermissionsRequested()
                                onboardingPermissionPending = true
                                requestPermissions()
                            } else {
                                showMissingPermissionsWarning = false
                                saveMissingPermissionsWarning(false)
                                showAppWithoutDefaultDialer = true
                                onboardingCompleted = true
                                saveOnboardingCompleted()
                            }
                        }
                    )
                    !showAppWithoutDefaultDialer && !DefaultDialerHelper.isDefaultDialer(this@MainActivity) ->
                        DefaultDialerRequestScreen(
                            onRequestDefaultDialer = {
                                DefaultDialerHelper.requestDefaultDialer(
                                    this@MainActivity,
                                    roleRequestLauncher = { intent -> defaultDialerLauncher.launch(intent) }
                                )
                            },
                            onOpenSettings = { DefaultDialerHelper.openDefaultAppsSettings(this@MainActivity) },
                            onSkip = { showAppWithoutDefaultDialer = true }
                        )
                    else -> PhoneAppContent(
                        activity = this@MainActivity,
                        initialPhoneNumber = initialPhoneNumber,
                        autoCall = autoCall
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        permissionsState = allGranted
    }

    private fun loadOnboardingCompleted(): Boolean {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("onboarding_completed", false)
    }

    private fun saveOnboardingCompleted() {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .apply()
    }

    private fun loadPermissionsRequested(): Boolean {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("has_requested_permissions", false)
    }

    private fun savePermissionsRequested() {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("has_requested_permissions", true)
            .apply()
    }

    private fun loadMissingPermissionsWarning(): Boolean {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("show_missing_permissions_warning", false)
    }

    private fun saveMissingPermissionsWarning(show: Boolean) {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("show_missing_permissions_warning", show)
            .apply()
    }

    private fun openAppSettings() {
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        android.util.Log.d("MainActivity", "onNewIntent: action=${intent.action}")
        handleIncomingCallIntent(intent)
    }
    
    override fun onResume() {
        super.onResume()
        checkPermissions()
        if (!permissionsState && onboardingCompleted) {
            showMissingPermissionsWarning = true
            saveMissingPermissionsWarning(true)
            showAppWithoutDefaultDialer = false
        }
    }

    fun startCallListeningService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, CallListeningService::class.java))
        } else {
            startService(Intent(this, CallListeningService::class.java))
        }
    }

    private fun handleIncomingCallIntent(intent: Intent?) {
        if (intent?.action == CallListeningService.ACTION_INCOMING_CALL) {
            val callId = intent.getStringExtra(CallListeningService.EXTRA_CALL_ID) ?: return
            val roomId = intent.getStringExtra(CallListeningService.EXTRA_ROOM_ID)
                ?: org.duckdns.dorandoran.callaiassistant.webrtc.WEBRTC_ROOM_ID
            val callerNumber = intent.getStringExtra(CallListeningService.EXTRA_CALLER_NUMBER).orEmpty()
            (application as? CallApp)?.callSignalingManager?.setIncomingFromIntent(
                org.duckdns.dorandoran.callaiassistant.webrtc.CallSignalingManager.IncomingCallInfo(
                    callId,
                    roomId,
                    callerNumber
                )
            )
        }
    }
}

@Composable
private fun PhoneAppContent(
    activity: MainActivity,
    initialPhoneNumber: String = "",
    autoCall: Boolean = false
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showWebRtcCall by remember { mutableStateOf(false) }
    var showIncomingCall by remember { mutableStateOf(false) }
    var webrtcPhoneNumber by remember { mutableStateOf("") }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerLocked by remember { mutableStateOf(false) }
    var lastCalledPhoneNumber by remember { mutableStateOf<String?>(null) }

    val callSignalingManager = remember { (activity.applicationContext as CallApp).callSignalingManager }
    val webRtcManager = remember { WebRtcManager(activity.applicationContext, callSignalingManager) }
    val callAudioManager = remember { CallAudioManager(activity.applicationContext) }
    val ringbackToneHelper = remember { RingbackToneHelper() }
    val coroutineScope = rememberCoroutineScope()

    val incomingCall by callSignalingManager.incomingCall.collectAsState()

    var autoCallConsumed by remember { mutableStateOf(false) }

    // WebRtcManager 콜백 설정 - 통화 종료 시 알림 취소
    androidx.compose.runtime.SideEffect {
        webRtcManager.onCallEnded = {
            activity.startService(Intent(activity, CallListeningService::class.java).apply {
                action = CallListeningService.ACTION_CALL_HANDLED
            })
        }
    }

    LaunchedEffect(Unit) {
        callSignalingManager.startListening()
        activity.startCallListeningService()
    }

    LaunchedEffect(incomingCall) {
        showIncomingCall = incomingCall != null
    }

    LaunchedEffect(initialPhoneNumber) {
        autoCallConsumed = false
    }

    fun startOutgoingCall(number: String) {
        lastCalledPhoneNumber = number
        webrtcPhoneNumber = number.ifBlank { "상대방" }
        callSignalingManager.markAsCaller()
        coroutineScope.launch {
            if (!callSignalingManager.isListening.value) {
                callSignalingManager.startListening()
                val ready = withTimeoutOrNull(2000) {
                    callSignalingManager.isListening.first { it }
                }
                if (ready != true) {
                    bannerMessage = "통화 연결을 준비 중입니다"
                    bannerLocked = true
                    return@launch
                }
            }
            val callerNumber = getOwnPhoneNumber(activity.applicationContext)
            val callId = callSignalingManager.initiateCall(callerNumber)
            if (callId.isBlank()) {
                bannerMessage = "통화 연결을 준비 중입니다"
                bannerLocked = true
                return@launch
            }
            callAudioManager.start()
            ringbackToneHelper.start()
            webRtcManager.joinAsCaller(callId)
            showWebRtcCall = true
        }
    }

    LaunchedEffect(autoCall, initialPhoneNumber) {
        if (autoCall && initialPhoneNumber.isNotBlank() && !autoCallConsumed) {
            autoCallConsumed = true
            startOutgoingCall(initialPhoneNumber)
        }
    }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(2000)
            bannerMessage = null
            bannerLocked = false
        }
    }

    // 통화 중(발신/수신)이면 수신 화면보다 통화 화면 우선 (발신자가 incoming 수신하는 문제 방지)
    if (showWebRtcCall) {
        // 통화 화면에서만 소프트키 숨기기
        DisposableEffect(Unit) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                hide(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.navigationBars())
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            }
        }
        WebRtcCallContent(
            phoneNumber = webrtcPhoneNumber,
            webRtcManager = webRtcManager,
            callAudioManager = callAudioManager,
            ringbackToneHelper = ringbackToneHelper,
            onCallRejected = {
                ringbackToneHelper.stop()
                callAudioManager.stop()
                webRtcManager.hangup()
                callSignalingManager.clearIncoming()
                callSignalingManager.clearCallerMode()
                callSignalingManager.startListening()
                activity.startCallListeningService()
                showWebRtcCall = false
                selectedTab = 0
                bannerMessage = "통화가 거절되었습니다"
                bannerLocked = true
            },
            onEndCall = {
                ringbackToneHelper.stop()
                callAudioManager.stop()
                webRtcManager.hangup()
                callSignalingManager.clearIncoming()
                callSignalingManager.clearCallerMode()
                callSignalingManager.startListening()
                activity.startCallListeningService()
                showWebRtcCall = false
                selectedTab = 0
                if (!bannerLocked) {
                    bannerMessage = "통화가 종료되었습니다"
                    bannerLocked = true
                }
            },
            onRemoteDisconnected = {
                ringbackToneHelper.stop()
                callAudioManager.stop()
                webRtcManager.hangup()
                callSignalingManager.clearIncoming()
                callSignalingManager.clearCallerMode()
                callSignalingManager.startListening()
                activity.startCallListeningService()
                showWebRtcCall = false
                selectedTab = 0
                if (!bannerLocked) {
                    bannerMessage = "통화가 종료되었습니다"
                    bannerLocked = true
                }
            }
        )
        return
    }

    if (showIncomingCall && incomingCall != null) {
        // 수신 전화 화면에서 소프트키 숨기기
        DisposableEffect(Unit) {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                hide(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.navigationBars())
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            }
        }
        
        IncomingCallScreen(
            callerName = incomingCall?.callerNumber?.ifBlank { "상대방" } ?: "상대방",
            onAccept = {
                val info = incomingCall!!
                // 알림 제거
                val notificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(1002)
                activity.startService(Intent(activity, CallListeningService::class.java).apply {
                    action = CallListeningService.ACTION_CALL_HANDLED
                })
                // accept는 listening 소켓에서 보내야 함
                coroutineScope.launch {
                    callSignalingManager.acceptCall(info.callId)
                    // 먼저 join을 시도; 리스닝 소켓은 닫지 않는다.
                    callAudioManager.start()
                    webrtcPhoneNumber = info.callerNumber.ifBlank { "상대방" }
                    webRtcManager.joinAsCallee(info.callId)
                    showIncomingCall = false
                    showWebRtcCall = true
                }
            },
            onReject = {
                val info = incomingCall!!
                // 알림 제거
                val notificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(1002)
                activity.startService(Intent(activity, CallListeningService::class.java).apply {
                    action = CallListeningService.ACTION_CALL_HANDLED
                })
                callSignalingManager.rejectCall(info.callId)
                callSignalingManager.clearIncoming()
                showIncomingCall = false
            }
        )
        return
    }

    // 다이얼 화면에서는 소프트키 표시
    DisposableEffect(Unit) {
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.navigationBars())
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        onDispose { }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "전화 기록") },
                    label = { Text("최근 기록") }
                )
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Call, contentDescription = "다이얼러") },
                    label = { Text("키패드") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                DialerScreen(
                    initialPhoneNumber = initialPhoneNumber,
                    lastCalledNumber = lastCalledPhoneNumber,
                    onCallStarted = { phoneNumber ->
                        startOutgoingCall(phoneNumber)
                    },
                    onOpenSettings = {
                        val intent = Intent(activity, SettingsActivity::class.java)
                        activity.startActivity(intent)
                    },
                    onOpenContactSearch = { query ->
                        val intent = Intent(activity, ContactSearchResultsActivity::class.java).apply {
                            putExtra(ContactSearchResultsActivity.EXTRA_QUERY, query)
                        }
                        activity.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (bannerMessage != null) {
                    androidx.compose.material3.Surface(
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                            .zIndex(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text = bannerMessage!!,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        }
                    }
                }
            }
            1 -> CallHistoryScreen(
                onCallNumber = { phoneNumber ->
                    callSignalingManager.clearIncoming()
                    startOutgoingCall(phoneNumber)
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

private fun getOwnPhoneNumber(context: Context): String {
    val storedNumber = SettingsStore.getMyPhoneNumber(context)
    if (storedNumber.isNotBlank()) {
        return storedNumber
    }

    val hasPhoneState = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED
    val hasPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_NUMBERS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        false
    }
    val hasReadSms = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPhoneState && !hasPhoneNumbers && !hasReadSms) {
        return ""
    }

    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val directNumber = try {
        telephonyManager?.line1Number.orEmpty()
    } catch (e: SecurityException) {
        ""
    }
    if (directNumber.isNotBlank()) {
        return directNumber
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val subscriptionNumber = try {
            subscriptionManager?.activeSubscriptionInfoList
                ?.firstOrNull { !it.number.isNullOrBlank() }
                ?.number
                .orEmpty()
        } catch (e: SecurityException) {
            ""
        }
        if (subscriptionNumber.isNotBlank()) {
            return subscriptionNumber
        }
    }

    return ""
}

@Composable
private fun WebRtcCallContent(
    phoneNumber: String,
    webRtcManager: WebRtcManager,
    callAudioManager: CallAudioManager,
    ringbackToneHelper: RingbackToneHelper,
    onCallRejected: () -> Unit,
    onEndCall: () -> Unit,
    onRemoteDisconnected: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by webRtcManager.connectionState.collectAsState()
    var callDuration by remember { mutableLongStateOf(0L) }
    var introPromptPlayed by remember { mutableStateOf(false) }
    var introTts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun stopIntroTts() {
        TtsManager.shutdown(introTts)
        introTts = null
    }

    LaunchedEffect(Unit) {
        webRtcManager.onRemoteDisconnected = {
            stopIntroTts()
            onRemoteDisconnected()
        }
        webRtcManager.onCallRejected = {
            stopIntroTts()
            onCallRejected()
        }
    }

    LaunchedEffect(connectionState) {
        when (connectionState) {
            org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState.IN_CALL,
            org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState.DISCONNECTED ->
                ringbackToneHelper.stop()
            else -> {}
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopIntroTts() }
    }

    LaunchedEffect(connectionState) {
        val inCall = connectionState == org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState.IN_CALL
        if (!inCall) {
            introPromptPlayed = false
            stopIntroTts()
            return@LaunchedEffect
        }
        if (introPromptPlayed) {
            return@LaunchedEffect
        }
        introPromptPlayed = true
        if (!SettingsStore.isCallIntroPromptEnabled(context)) {
            return@LaunchedEffect
        }
        val style = SettingsStore.getCallIntroPromptStyle(context)
        val message = getIntroPromptMessage(style)
        if (message.isBlank()) {
            return@LaunchedEffect
        }
        val useVoiceClone = SettingsStore.isVoiceCloneEnabled(context)
        introTts = TtsManager.initializeForCall(
            context = context,
            onReady = { tts ->
                introTts = tts
                if (useVoiceClone) {
                    TtsManager.speak(tts, message, audioManager)
                } else {
                    TtsManager.speak(tts, message, audioManager)
                }
            },
            onDone = {
                TtsManager.shutdown(introTts)
                introTts = null
            }
        )
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

    val logMessages by webRtcManager.logMessages.collectAsState()
    val handleEndCall = {
        stopIntroTts()
        onEndCall()
    }

    WebRtcInCallScreen(
        phoneNumber = phoneNumber,
        connectionState = connectionState,
        callDurationSeconds = callDuration,
        logMessages = logMessages,
        onEndCall = handleEndCall,
        onSpeakerphoneToggle = { isOn ->
            callAudioManager.setSpeakerphone(isOn)
        }
    )
}

private fun getIntroPromptMessage(style: String): String {
    return when (style) {
        CallIntroPromptStyle.BASIC.value -> 
            "안녕하세요, 원활한 소통을 위해 AI 음성 변환 서비스를 이용중입니다. 제 말이 조금 늦더라도 양해 부탁드립니다"
        CallIntroPromptStyle.SITUATION.value ->
            "안녕하세요. 청각/언어의 어려움으로 텍스트를 음성으로 변환하여 대화하고 있습니다. 천천히 말씀해 주시면 감사하겠습니다."
        CallIntroPromptStyle.ASSISTANT.value ->
            "안녕하세요. 지금은 AI 통화 비서가 대화를 돕고 있습니다. 문자로 입력한 내용을 음성으로 전달해 드릴게요."
        else ->
            ""
    }
}

@Composable
private fun MissingPermissionsWarningScreen(
    onOpenSettings: () -> Unit,
    onCloseApp: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "필수 권한이 누락되어\n전화 기능을 사용할 수 없습니다",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "설정에서 권한을 허용한 뒤\n앱을 다시 실행해주세요",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        androidx.compose.material3.OutlinedButton(onClick = onOpenSettings) {
                            Text("설정 열기")
                        }
                        Button(onClick = onCloseApp) {
                            Text("앱 종료")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultDialerRequestScreen(
    onRequestDefaultDialer: () -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "전화 앱 사용을 위해\n기본 전화 앱으로 설정해주세요",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                androidx.compose.material3.Button(onClick = onRequestDefaultDialer) {
                    Text("기본 전화 앱으로 설정")
                }
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(onClick = onOpenSettings) {
                    Text("설정에서 직접 열기")
                }
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = onSkip) {
                    Text("나중에")
                }
            }
        }
    }
}

@Composable
private fun OnboardingFlow(
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    when (step) {
        0 -> OnboardingIntroScreen(onStart = { step = 1 })
        else -> OnboardingPermissionsScreen(onAgree = onComplete)
    }
}

@Composable
private fun OnboardingIntroScreen(onStart: () -> Unit) {
    val isDarkTheme = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDarkTheme) Color(0xFF101214) else Color.White
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF8B5CF6), Color(0xFF22C55E)),
                            start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
                            end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "T.mate",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text("시작하기")
            }
        }
    }
}

@Composable
private fun OnboardingPermissionsScreen(onAgree: () -> Unit) {
    val context = LocalContext.current
    var allChecked by remember { mutableStateOf(false) }
    var serviceTermsChecked by remember { mutableStateOf(false) }
    var privacyCollectionChecked by remember { mutableStateOf(false) }
    var privacyThirdPartyChecked by remember { mutableStateOf(false) }
    var callTextProcessingChecked by remember { mutableStateOf(false) }

    fun updateAllFromChildren() {
        allChecked = serviceTermsChecked && privacyCollectionChecked &&
            privacyThirdPartyChecked && callTextProcessingChecked
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "T.mate\n원활한 통화를 위해\n약관에 동의가 필요합니다",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            val next = !allChecked
                            allChecked = next
                            serviceTermsChecked = next
                            privacyCollectionChecked = next
                            privacyThirdPartyChecked = next
                            callTextProcessingChecked = next
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allChecked,
                        onCheckedChange = { checked ->
                            allChecked = checked
                            serviceTermsChecked = checked
                            privacyCollectionChecked = checked
                            privacyThirdPartyChecked = checked
                            callTextProcessingChecked = checked
                        }
                    )
                    Text(
                        text = "필수 약관 모두 동의",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Checkbox(
                        checked = serviceTermsChecked,
                        onCheckedChange = { checked ->
                            serviceTermsChecked = checked
                            updateAllFromChildren()
                        }
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                serviceTermsChecked = !serviceTermsChecked
                                updateAllFromChildren()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "(필수) 서비스 이용약관 동의",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "서비스 이용에 필요한 기본 규칙과 책임 규정",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                            .clickable {
                                context.startActivity(
                                    Intent(context, ServiceTermsActivity::class.java)
                                )
                            }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Checkbox(
                        checked = privacyCollectionChecked,
                        onCheckedChange = { checked ->
                            privacyCollectionChecked = checked
                            updateAllFromChildren()
                        }
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                privacyCollectionChecked = !privacyCollectionChecked
                                updateAllFromChildren()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "(필수) 개인정보 수집 및 이용 동의",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "본인 확인 및 원활한 서비스 운영에 필요",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                            .clickable {
                                context.startActivity(
                                    Intent(context, PrivacyCollectionActivity::class.java)
                                )
                            }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Checkbox(
                        checked = privacyThirdPartyChecked,
                        onCheckedChange = { checked ->
                            privacyThirdPartyChecked = checked
                            updateAllFromChildren()
                        }
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                privacyThirdPartyChecked = !privacyThirdPartyChecked
                                updateAllFromChildren()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "(필수) 개인정보 제3자 제공 동의",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "AI 답변 생성 및 발음 교정 처리에 필요",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                            .clickable {
                                context.startActivity(
                                    Intent(context, PrivacyThirdPartyActivity::class.java)
                                )
                            }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Checkbox(
                        checked = callTextProcessingChecked,
                        onCheckedChange = { checked ->
                            callTextProcessingChecked = checked
                            updateAllFromChildren()
                        }
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                callTextProcessingChecked = !callTextProcessingChecked
                                updateAllFromChildren()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "(필수) 통화 내용 텍스트 변환 및 처리 동의",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "실시간 통화 음성을 텍스트로 변환하는데 필요",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                            .clickable {
                                context.startActivity(
                                    Intent(context, CallTextProcessingActivity::class.java)
                                )
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val agreeEnabled = allChecked
            Button(
                onClick = onAgree,
                enabled = agreeEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (agreeEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (agreeEnabled) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            ) {
                Text("동의")
            }
        }
    }
}
