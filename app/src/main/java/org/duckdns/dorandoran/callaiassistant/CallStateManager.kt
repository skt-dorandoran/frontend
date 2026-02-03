package org.duckdns.dorandoran.callaiassistant

import android.content.Context
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * 전화 통화 상태를 감지하는 매니저
 */
class CallStateManager(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val _callState = MutableStateFlow(CallUiState.IDLE)
    val callState: StateFlow<CallUiState> = _callState.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

    private var callStartTime: Long = 0
    private var durationUpdateRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> _callState.value = CallUiState.RINGING
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    _callState.value = CallUiState.ACTIVE
                    callStartTime = System.currentTimeMillis()
                    startDurationTimer()
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    _callState.value = CallUiState.IDLE
                    stopDurationTimer()
                }
            }
        }
    }

    fun startListening() {
        telephonyManager.registerTelephonyCallback(Executors.newSingleThreadExecutor(), telephonyCallback)
    }

    fun stopListening() {
        telephonyManager.unregisterTelephonyCallback(telephonyCallback)
    }

    fun setDialing() {
        _callState.value = CallUiState.DIALING
    }

    private fun startDurationTimer() {
        stopDurationTimer()
        durationUpdateRunnable = object : Runnable {
            override fun run() {
                if (_callState.value == CallUiState.ACTIVE) {
                    _callDurationSeconds.value = (System.currentTimeMillis() - callStartTime) / 1000
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(durationUpdateRunnable!!)
    }

    private fun stopDurationTimer() {
        durationUpdateRunnable?.let { handler.removeCallbacks(it) }
        durationUpdateRunnable = null
    }

    fun reset() {
        _callState.value = CallUiState.IDLE
        _callDurationSeconds.value = 0L
        stopDurationTimer()
    }
}

enum class CallUiState {
    IDLE,       // 통화 없음
    DIALING,    // 연결 중
    RINGING,    // 벨 울림 (수신)
    ACTIVE      // 통화 중
}
