package org.duckdns.dorandoran.callaiassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 부팅 완료 시 CallListeningService 자동 시작
 * 참고: 앱 강제 종료 후에는 Android 정책상 자동 재시작 불가.
 * 사용자가 앱을 한 번이라도 실행하면 서비스가 시작됨.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val serviceIntent = Intent(context, CallListeningService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
