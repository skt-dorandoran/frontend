package org.duckdns.dorandoran.callaiassistant

import android.telecom.Call
import android.telecom.VideoProfile
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * InCallService와 InCallActivity 간 통화 정보 공유
 */
object InCallManager {

    @Volatile
    private var currentCall: Call? = null

    /** 스피커폰 상태 (InCallService.onCallAudioStateChanged에서 갱신) */
    val speakerState: MutableState<Boolean> = mutableStateOf(false)

    fun setCurrentCall(call: Call?) {
        currentCall = call
    }

    fun getPrimaryCall(): Call? = currentCall

    fun getCallNumber(call: Call): String {
        val handle = call.details?.handle
        return handle?.schemeSpecificPart ?: ""
    }

    fun updateSpeakerState(on: Boolean) {
        speakerState.value = on
    }

    /**
     * 스피커폰 on/off 전환 (InCallService.setAudioRoute 사용 - Telecom API)
     */
    fun setSpeakerphone(on: Boolean) {
        AppInCallService.instance?.setSpeakerphone(on)
    }

    /**
     * 수신 전화 받기 (STATE_RINGING 상태에서만 호출)
     */
    fun answer(call: Call) {
        call.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun disconnect(call: Call) {
        call.disconnect()
    }
}
