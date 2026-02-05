package org.duckdns.dorandoran.callaiassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 수신 전화 인텐트를 받아서 MainActivity를 foreground로 시작
 */
class IncomingCallBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        if (intent.action == CallListeningService.ACTION_INCOMING_CALL) {
            Log.d("IncomingCallBroadcastReceiver", "Received incoming call intent")
            
            val callId = intent.getStringExtra(CallListeningService.EXTRA_CALL_ID) ?: ""
            val roomId = intent.getStringExtra(CallListeningService.EXTRA_ROOM_ID) ?: ""
            
            // InCallActivity를 직접 시작하여 수신 화면을 보이게 시도
            val inCallIntent = Intent(context, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                // 잠금 화면 위에 표시
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    addFlags(0x00000800) // FLAG_ACTIVITY_SHOW_WHEN_LOCKED
                } else {
                    addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                }
                action = CallListeningService.ACTION_INCOMING_CALL
                putExtra(CallListeningService.EXTRA_CALL_ID, callId)
                putExtra(CallListeningService.EXTRA_ROOM_ID, roomId)
            }

            try {
                Log.d("IncomingCallBroadcastReceiver", "Starting InCallActivity with incoming call intent")
                context.startActivity(inCallIntent)
            } catch (e: Exception) {
                Log.e("IncomingCallBroadcastReceiver", "Failed to start InCallActivity: ${e.message}", e)
            }
        }
    }
}
