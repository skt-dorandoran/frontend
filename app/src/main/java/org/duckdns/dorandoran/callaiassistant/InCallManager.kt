package org.duckdns.dorandoran.callaiassistant

import android.telecom.Call
import android.telecom.VideoProfile

/**
 * InCallService와 InCallActivity 간 통화 정보 공유
 */
object InCallManager {

    @Volatile
    private var currentCall: Call? = null

    fun setCurrentCall(call: Call?) {
        currentCall = call
    }

    fun getPrimaryCall(): Call? = currentCall

    fun getCallNumber(call: Call): String {
        val handle = call.details?.handle
        return handle?.schemeSpecificPart ?: ""
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
