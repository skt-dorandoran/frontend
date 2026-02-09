package org.duckdns.dorandoran.callaiassistant

import android.app.Application
import org.duckdns.dorandoran.callaiassistant.webrtc.CallSignalingManager

class CallApp : Application() {

    val callSignalingManager: CallSignalingManager by lazy {
        CallSignalingManager(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
    }
}
