package org.duckdns.dorandoran.callaiassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 부팅 완료 및 앱 업데이트 시 CallListeningService 자동 시작
 * - BOOT_COMPLETED: 휴대폰 부팅 완료 시
 * - MY_PACKAGE_REPLACED: 앱 업데이트 완료 시
 * - QUICKBOOT_POWERON: 일부 기기의 빠른 부팅
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received action: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("BootReceiver", "Starting CallListeningService...")
            val serviceIntent = Intent(context, CallListeningService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}

