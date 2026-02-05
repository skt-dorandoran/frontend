package org.duckdns.dorandoran.callaiassistant

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    }

    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* 결과는 onResume에서 isDefaultDialer로 확인 */ }

    private var permissionsState by mutableStateOf(false)
    private var showAppWithoutDefaultDialer by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()
        
        // 앱 시작 시 CallListeningService 시작 (백그라운드 청취용)
        startCallListeningService()

        val initialPhoneNumber = intent?.data?.takeIf { it.scheme == "tel" }
            ?.schemeSpecificPart?.orEmpty()?.filter { c -> c.isDigit() || c == '+' } ?: ""
        val autoCall = intent?.getBooleanExtra(EXTRA_AUTO_CALL, false) ?: false

        handleIncomingCallIntent(intent)

        setContent {
            CallaiassistantTheme {
                when {
                    !permissionsState -> PermissionRequestScreen(
                        onRequestPermission = { requestPermissions() }
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
            (application as? CallApp)?.callSignalingManager?.setIncomingFromIntent(
                org.duckdns.dorandoran.callaiassistant.webrtc.CallSignalingManager.IncomingCallInfo(callId, roomId)
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
    remember {
        webRtcManager.onCallEnded = {
            activity.startService(Intent(activity, CallListeningService::class.java).apply {
                action = CallListeningService.ACTION_CALL_HANDLED
            })
        }
        Unit
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
        callSignalingManager.initiateCall()
        callAudioManager.start()
        ringbackToneHelper.start()
        webRtcManager.joinAsCaller("")
        showWebRtcCall = true
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
            callerName = "상대방",
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
                    webrtcPhoneNumber = "상대방"
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
    val connectionState by webRtcManager.connectionState.collectAsState()
    var callDuration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        webRtcManager.onRemoteDisconnected = { onRemoteDisconnected() }
        webRtcManager.onCallRejected = { onCallRejected() }
    }

    LaunchedEffect(connectionState) {
        when (connectionState) {
            org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState.IN_CALL,
            org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState.DISCONNECTED ->
                ringbackToneHelper.stop()
            else -> {}
        }
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
    WebRtcInCallScreen(
        phoneNumber = phoneNumber,
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
private fun PermissionRequestScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Button(onClick = onRequestPermission) {
            Text("전화 권한 허용")
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
