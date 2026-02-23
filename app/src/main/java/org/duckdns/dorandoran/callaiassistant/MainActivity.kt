package org.duckdns.dorandoran.callaiassistant

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewmodel.compose.viewModel
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
import org.duckdns.dorandoran.callaiassistant.ui.screens.DialerScreen
import org.duckdns.dorandoran.callaiassistant.ui.screens.IncomingCallScreen
import org.duckdns.dorandoran.callaiassistant.ui.navigation.CallNavHost
import org.duckdns.dorandoran.callaiassistant.webrtc.CallAudioManager
import org.duckdns.dorandoran.callaiassistant.webrtc.RingbackToneHelper
import org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcManager
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme
import org.duckdns.dorandoran.callaiassistant.tts.TtsManager
import org.duckdns.dorandoran.callaiassistant.tts.SherpaOnnxTtsManager
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import org.duckdns.dorandoran.callaiassistant.ui.screens.OnboardingPermissionsScreen
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel
import androidx.compose.ui.text.font.Font
import org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneStore
import org.duckdns.dorandoran.callaiassistant.voiceclone.VoiceCloneTtsApi
import org.duckdns.dorandoran.callaiassistant.util.ContactLookupUtil
import org.duckdns.dorandoran.callaiassistant.util.rememberContactsVersion
import java.io.File

val Pretendard = FontFamily(
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTO_CALL = "extra_auto_call"
    }

    private val corePermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Manifest.permission.READ_PHONE_NUMBERS)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }.toTypedArray()

    private val optionalPermissions: Array<String>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkPermissions()
        val allGranted = permissionsState
        savePermissionsRequested()
        onboardingCompleted = true
        saveOnboardingCompleted()
        onboardingPermissionPending = false
        if (!allGranted) {
            showMissingPermissionsWarning = true
            saveMissingPermissionsWarning(true)
            showAppWithoutDefaultDialer = false
            return@registerForActivityResult
        }
        showMissingPermissionsWarning = false
        saveMissingPermissionsWarning(false)
        showAppWithoutDefaultDialer = true
        initializeMyPhoneNumberDefault()
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

        ensureFirstLaunchOnboardingGate()
        checkPermissions()
        hasRequestedPermissions = loadPermissionsRequested()
        onboardingCompleted = loadOnboardingCompleted()
        showMissingPermissionsWarning = loadMissingPermissionsWarning()
        if (!hasRequestedPermissions) {
            // 최초 실행에서는 반드시 온보딩을 먼저 거치도록 강제한다.
            onboardingCompleted = false
            showMissingPermissionsWarning = false
            saveMissingPermissionsWarning(false)
            showAppWithoutDefaultDialer = false
        } else if (permissionsState) {
            showMissingPermissionsWarning = false
            saveMissingPermissionsWarning(false)
            showAppWithoutDefaultDialer = true
            initializeMyPhoneNumberDefault()
        } else if (onboardingCompleted && hasRequestedPermissions) {
            showMissingPermissionsWarning = true
            saveMissingPermissionsWarning(true)
            showAppWithoutDefaultDialer = false
        }

        val initialPhoneNumber = intent?.data?.takeIf { it.scheme == "tel" }
            ?.schemeSpecificPart?.orEmpty()?.filter { c -> c.isDigit() || c == '+' } ?: ""
        val autoCall = intent?.getBooleanExtra(EXTRA_AUTO_CALL, false) ?: false

        handleIncomingCallIntent(intent)

        setContent {
            CallaiassistantTheme {
                when {
                    // 1. 권한 거부 시 경고 화면
                    showMissingPermissionsWarning && onboardingCompleted && hasRequestedPermissions -> MissingPermissionsWarningScreen(
                        onOpenSettings = { openAppSettings() },
                        onCloseApp = { finish() }
                    )

                    !onboardingCompleted -> OnboardingFlow(
                        onComplete = {
                            // "약관 동의 완료" 시 -> 권한 요청 로직 실행
                            if (!permissionsState) {
                                hasRequestedPermissions = true
                                savePermissionsRequested()
                                onboardingPermissionPending = true
                                requestPermissions()
                            } else {
                                // 이미 권한이 있다면 완료 처리 후 메인으로
                                showMissingPermissionsWarning = false
                                saveMissingPermissionsWarning(false)
                                showAppWithoutDefaultDialer = true
                                onboardingCompleted = true
                                saveOnboardingCompleted()
                            }
                        }
                    )

                    // 3. 기본 전화 앱 설정 필요 시
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

                    // 4. 모든 설정 완료 시 메인 앱(다이얼러 등) 화면
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
        val allGranted = corePermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        permissionsState = allGranted
    }

    private fun loadOnboardingCompleted(): Boolean {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("onboarding_completed", false)
    }

    private fun ensureFirstLaunchOnboardingGate() {
        val marker = File(noBackupFilesDir, "onboarding_initialized_v1")
        if (marker.exists()) return

        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("has_requested_permissions", false)
            .putBoolean("onboarding_completed", false)
            .putBoolean("show_missing_permissions_warning", false)
            .apply()

        runCatching {
            marker.writeText("1")
        }
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
        permissionLauncher.launch(corePermissions + optionalPermissions)
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
        if (permissionsState) {
            showMissingPermissionsWarning = false
            saveMissingPermissionsWarning(false)
            if (hasRequestedPermissions && !onboardingCompleted) {
                onboardingCompleted = true
                saveOnboardingCompleted()
            }
            if (onboardingCompleted) {
                showAppWithoutDefaultDialer = true
            }
            initializeMyPhoneNumberDefault()
            if (onboardingCompleted && hasRequestedPermissions) {
                ensureUnrestrictedBatteryUsage()
            }
        }
        if (!permissionsState && onboardingCompleted && hasRequestedPermissions) {
            showMissingPermissionsWarning = true
            saveMissingPermissionsWarning(true)
            showAppWithoutDefaultDialer = false
        } else if (!permissionsState && !hasRequestedPermissions) {
            showMissingPermissionsWarning = false
            saveMissingPermissionsWarning(false)
        }
    }

    private fun ensureUnrestrictedBatteryUsage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastPromptAt = prefs.getLong("battery_unrestricted_prompt_at", 0L)
        if (now - lastPromptAt < 60_000L) {
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            return
        }

        prefs.edit().putLong("battery_unrestricted_prompt_at", now).apply()

        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }

        try {
            startActivity(requestIntent)
        } catch (_: Exception) {
            // Some devices block direct request screens; open the optimization list as fallback.
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                Toast.makeText(
                    this,
                    "배터리 사용을 '제한 없음'으로 설정해 주세요.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                )
            }
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

    private fun initializeMyPhoneNumberDefault() {
        if (!canReadDevicePhoneNumber(this)) {
            return
        }
        SettingsStore.ensureMyPhoneNumberDefault(this, getDevicePhoneNumber(this))
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
    val prefs = remember { activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var lastCalledPhoneNumber by remember {
        mutableStateOf(
            prefs.getString("last_called_phone_number", null)
                ?.filter { it.isDigit() }
                ?.ifBlank { null }
        )
    }

    val callSignalingManager = remember { (activity.applicationContext as CallApp).callSignalingManager }
    val webRtcManager = remember { WebRtcManager(activity.applicationContext, callSignalingManager) }
    val callAudioManager = remember { CallAudioManager(activity.applicationContext) }
    val ringbackToneHelper = remember { RingbackToneHelper() }
    val coroutineScope = rememberCoroutineScope()

    val incomingCall by callSignalingManager.incomingCall.collectAsState()
    val contactsVersion = rememberContactsVersion(activity.applicationContext)
    val incomingDisplayInfo by produceState(
        initialValue = ContactLookupUtil.DisplayInfo(primary = "상대방", secondary = ""),
        key1 = incomingCall?.callerNumber,
        key2 = contactsVersion
    ) {
        value = ContactLookupUtil.resolveDisplayInfo(activity.applicationContext, incomingCall?.callerNumber.orEmpty())
    }

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

    fun persistLastCalledNumber(number: String) {
        val normalized = number.filter { it.isDigit() }
        if (normalized.isBlank()) return
        lastCalledPhoneNumber = normalized
        prefs.edit().putString("last_called_phone_number", normalized).apply()
    }

    fun startOutgoingCall(number: String) {
        persistLastCalledNumber(number)
        webrtcPhoneNumber = number.ifBlank { "상대방" }
        coroutineScope.launch {
            if (!callSignalingManager.isListening.value) {
                callSignalingManager.startListening()
                val ready = withTimeoutOrNull(2000) {
                    callSignalingManager.isListening.first { it }
                }
                if (ready != true) {
                    callSignalingManager.clearCallerMode()
                    bannerMessage = "통화 연결을 준비 중입니다"
                    bannerLocked = true
                    return@launch
                }
            }
            val callerNumber = getOwnPhoneNumber(activity.applicationContext)
            callSignalingManager.markAsCaller()
            val callId = callSignalingManager.initiateCall(callerNumber, number)
            if (callId.isBlank()) {
                callSignalingManager.clearCallerMode()
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

    // 통화 중(발신/수신)이면 수신 화면보다 통화 화면 우선
    if (showWebRtcCall) {
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
            callerName = incomingDisplayInfo.primary,
            callerNumber = incomingDisplayInfo.secondary,
            onAccept = {
                val info = incomingCall!!
                val notificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(1002)
                activity.startService(Intent(activity, CallListeningService::class.java).apply {
                    action = CallListeningService.ACTION_CALL_HANDLED
                })
                coroutineScope.launch {
                    callSignalingManager.acceptCall(info.callId)
                    callAudioManager.start()
                    webrtcPhoneNumber = info.callerNumber.ifBlank { "상대방" }
                    webRtcManager.joinAsCallee(info.callId)
                    showIncomingCall = false
                    showWebRtcCall = true
                }
            },
            onReject = {
                val info = incomingCall!!
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

    DisposableEffect(Unit) {
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.navigationBars())
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        onDispose { }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White
    ) { innerPadding ->
        when (selectedTab) {
            0 -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                DialerScreen(
                    onOpenCallHistory = { selectedTab = 1 },
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
                onOpenDialer = { selectedTab = 0 },
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

    return getDevicePhoneNumber(context)
}

private fun canReadDevicePhoneNumber(context: Context): Boolean {
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

    return hasPhoneState || hasPhoneNumbers || hasReadSms
}

private fun getDevicePhoneNumber(context: Context): String {
    if (!canReadDevicePhoneNumber(context)) {
        return ""
    }

    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val directNumber = try {
        telephonyManager?.line1Number.orEmpty()
    } catch (e: SecurityException) {
        ""
    }
    if (directNumber.isNotBlank()) {
        return directNumber.filter { it.isDigit() }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val modernNumber = try {
                subscriptionManager?.activeSubscriptionInfoList
                    ?.asSequence()
                    ?.mapNotNull { info ->
                        subscriptionManager.getPhoneNumber(info.subscriptionId)
                            ?.filter { it.isDigit() }
                            ?.takeIf { it.isNotBlank() }
                    }
                    ?.firstOrNull()
                    .orEmpty()
            } catch (e: SecurityException) {
                ""
            }
            if (modernNumber.isNotBlank()) {
                return modernNumber
            }
        }

        val subscriptionNumber = try {
            subscriptionManager?.activeSubscriptionInfoList
                ?.firstOrNull { !it.number.isNullOrBlank() }
                ?.number
                .orEmpty()
        } catch (e: SecurityException) {
            ""
        }
        if (subscriptionNumber.isNotBlank()) {
            return subscriptionNumber.filter { it.isDigit() }
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
    val callViewModel: CallViewModel = viewModel()
    val connectionState by webRtcManager.connectionState.collectAsState()
    var callDuration by remember { mutableLongStateOf(0L) }
    var remoteSttStarted by remember { mutableStateOf(false) }
    var localSttStarted by remember { mutableStateOf(false) }
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
        onDispose {
            stopIntroTts()
            if (remoteSttStarted) {
                webRtcManager.stopRemoteStt()
                remoteSttStarted = false
            }
            if (localSttStarted) {
                webRtcManager.stopLocalStt()
                localSttStarted = false
            }
        }
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
        val message = getIntroPromptMessage(context, style)
        if (message.isBlank()) {
            return@LaunchedEffect
        }
        val useVoiceClone = SettingsStore.isVoiceCloneEnabled(context)
        val voiceId = VoiceCloneStore.getVoiceId(context)
        if (useVoiceClone && !voiceId.isNullOrBlank()) {
            val callIdStr = "call_${System.currentTimeMillis()}"
            val contextSafe = context.applicationContext
            val wavFile = VoiceCloneTtsApi.synthesizeVoiceClone(
                callId = callIdStr,
                text = message,
                voiceId = voiceId,
                sourceType = "ai_response",
                context = contextSafe
            )
            if (wavFile != null && wavFile.exists()) {
                SherpaOnnxTtsManager.playWavFile(wavFile, audioManager)
            } else {
                introTts = TtsManager.initializeForCall(
                    context = context,
                    onReady = { tts ->
                        introTts = tts
                        TtsManager.speak(
                            tts = tts,
                            text = message,
                            audioManager = audioManager,
                            onDone = {
                                TtsManager.shutdown(introTts)
                                introTts = null
                            }
                        )
                    }
                )
            }
        } else {
            introTts = TtsManager.initializeForCall(
                context = context,
                onReady = { tts ->
                    introTts = tts
                    TtsManager.speak(
                        tts = tts,
                        text = message,
                        audioManager = audioManager,
                        onDone = {
                            TtsManager.shutdown(introTts)
                            introTts = null
                        }
                    )
                }
            )
        }
    }

    LaunchedEffect(connectionState) {
        val callActive =
            connectionState != org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState.DISCONNECTED
        if (callActive && !remoteSttStarted) {
            webRtcManager.startRemoteStt(context.applicationContext, callViewModel)
            remoteSttStarted = true
        } else if (!callActive && remoteSttStarted) {
            webRtcManager.stopRemoteStt()
            remoteSttStarted = false
        }
    }

    LaunchedEffect(connectionState) {
        val callActive =
            connectionState != org.duckdns.dorandoran.callaiassistant.webrtc.WebRtcConnectionState.DISCONNECTED
        if (callActive && !localSttStarted) {
            webRtcManager.startLocalStt(context.applicationContext, callViewModel)
            localSttStarted = true
        } else if (!callActive && localSttStarted) {
            webRtcManager.stopLocalStt()
            localSttStarted = false
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
    val handleEndCall = {
        stopIntroTts()
        onEndCall()
    }

    CallNavHost(
        phoneNumber = phoneNumber,
        connectionState = connectionState,
        callDurationSeconds = callDuration,
        webRtcManager = webRtcManager,
        logMessages = logMessages,
        onEndCall = handleEndCall,
        onSpeakerphoneToggle = { isOn ->
            callAudioManager.setSpeakerphone(isOn)
            webRtcManager.setSpeakerphoneHint(isOn)
        },
        onLocalAudioTransmissionToggle = { enabled ->
            webRtcManager.setLocalAudioTransmissionEnabled(enabled)
        },
        enableIntroPromptPlayback = false,
        callViewModel = callViewModel
    )
}

private fun getIntroPromptMessage(context: Context, style: String): String {
    return when (style) {
        CallIntroPromptStyle.BASIC.value ->
            "안녕하세요, 원활한 소통을 위해 AI 음성 변환 서비스를 이용중입니다. 제 말이 조금 늦더라도 양해 부탁드립니다"
        CallIntroPromptStyle.SITUATION.value ->
            "안녕하세요. 청각/언어의 어려움으로 텍스트를 음성으로 변환하여 대화하고 있습니다. 천천히 말씀해 주시면 감사하겠습니다."
        CallIntroPromptStyle.ASSISTANT.value ->
            "안녕하세요. 지금은 AI 통화 비서가 대화를 돕고 있습니다. 문자로 입력한 내용을 음성으로 전달해 드릴게요."
        CallIntroPromptStyle.CUSTOM.value ->
            SettingsStore.getCallIntroPromptCustom(context)
                .ifBlank { "안녕하세요. 문자로 입력한 내용을 음성으로 안내해 드릴게요." }
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
        0 -> OnboardingIntroScreen(onStart = { step = 1 }) // 1. 인트로 끝나면 step 1로 이동
        else -> OnboardingPermissionsScreen(onAgreeComplete = onComplete) // 2. 약관 동의 끝나면 onComplete(권한 요청) 실행
    }
}

// [수정됨] OnboardingIntroScreen 복구
@Composable
fun OnboardingIntroScreen(onStart: () -> Unit) {
    // 2.5초 후 자동으로 다음 화면으로 이동
    LaunchedEffect(Unit) {
        delay(2500L) // 5500L은 너무 길어서 2.5초로 줄였습니다. 원하시면 5500L로 수정하세요.
        onStart()
    }

    // 전체화면 컨테이너
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        // 중앙 로고 컨테이너
        Box(
            modifier = Modifier
                .size(325.dp)
                .background(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFFA962FF),
                            0.85f to Color(0xFF5DEECB)
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
                        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // T.mate 텍스트
            // T.mate 텍스트
            Text(
                text = "T.mate",
                style = TextStyle(
                    fontSize = 45.sp,
                    lineHeight = 45.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            )
        }
    }
}
