package org.duckdns.dorandoran.callaiassistant

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
class AppInCallService : InCallService() {

    companion object {
        @Volatile
        var instance: AppInCallService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        InCallManager.setCurrentCall(call)
        updateSpeakerStateFromAudioState()
        startInCallActivity()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        InCallManager.setCurrentCall(null)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        updateSpeakerStateFromAudioState()
    }

    private fun updateSpeakerStateFromAudioState() {
        val state = callAudioState ?: return
        InCallManager.updateSpeakerState(
            (state.route and CallAudioState.ROUTE_SPEAKER) != 0
        )
    }

    fun setSpeakerphone(on: Boolean) {
        val route = if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        setAudioRoute(route)
        InCallManager.updateSpeakerState(on)
    }

    private fun startInCallActivity() {
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }
}
