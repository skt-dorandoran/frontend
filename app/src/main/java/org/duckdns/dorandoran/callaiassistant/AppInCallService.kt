package org.duckdns.dorandoran.callaiassistant

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class AppInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        InCallManager.setCurrentCall(call)
        startInCallActivity()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        InCallManager.setCurrentCall(null)
    }

    private fun startInCallActivity() {
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // NO_HISTORY 제거: 홈 버튼 후 최근 작업에서 복귀 가능하도록
        }
        startActivity(intent)
    }
}
