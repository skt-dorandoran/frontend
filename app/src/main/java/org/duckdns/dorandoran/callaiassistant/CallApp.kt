package org.duckdns.dorandoran.callaiassistant

import android.app.Application
import android.content.Intent
import android.os.Build
import org.duckdns.dorandoran.callaiassistant.webrtc.CallSignalingManager

class CallApp : Application() {

    val callSignalingManager: CallSignalingManager by lazy {
        CallSignalingManager(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        // 앱 시작 시 수신 대기 서비스 시작 (권한 화면에서도 대기)
        startCallListeningService()
    }

    private fun startCallListeningService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, CallListeningService::class.java))
        } else {
            startService(Intent(this, CallListeningService::class.java))
        }
    }
}
