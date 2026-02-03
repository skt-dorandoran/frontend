package org.duckdns.dorandoran.callaiassistant

import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import org.duckdns.dorandoran.callaiassistant.data.CallLogRepository
import org.duckdns.dorandoran.callaiassistant.ui.screens.CallState
import org.duckdns.dorandoran.callaiassistant.ui.screens.InCallScreen
import org.duckdns.dorandoran.callaiassistant.ui.theme.CallaiassistantTheme

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CallaiassistantTheme {
                InCallContent(
                    onFinish = { finish() }
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        InCallManager.getPrimaryCall()?.let { InCallManager.disconnect(it) }
        super.onBackPressed()
    }
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
                    Call.STATE_DIALING, Call.STATE_RINGING -> CallState.DIALING
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
            Call.STATE_ACTIVE -> {
                callStartTime = System.currentTimeMillis()
                CallState.ACTIVE
            }
            else -> CallState.DIALING
        }
        onDispose { call.unregisterCallback(callback) }
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
        onEndCall = {
            InCallManager.disconnect(call)
            onFinish()
        },
        modifier = Modifier.fillMaxSize()
    )
}
