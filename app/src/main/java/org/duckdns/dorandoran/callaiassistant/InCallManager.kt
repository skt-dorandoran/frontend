package org.duckdns.dorandoran.callaiassistant

import android.telecom.Call

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

    fun disconnect(call: Call) {
        call.disconnect()
    }
}
