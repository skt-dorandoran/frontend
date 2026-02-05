package org.duckdns.dorandoran.callaiassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.os.Handler
import android.os.Looper
import org.duckdns.dorandoran.callaiassistant.webrtc.CallSignalingManager.IncomingCallInfo

/**
 * 포그라운드 서비스 - 수신 전화 대기
 * 앱이 백그라운드에 있어도 WebSocket 유지, 수신 시 전체화면 인텐트로 수신 화면 표시
 */
class CallListeningService : Service() {

    companion object {
        private const val CHANNEL_ID = "call_listening"
        private const val CHANNEL_ID_INCOMING = "incoming_call"
        const val NOTIFICATION_ID = 1001
        const val ACTION_INCOMING_CALL = "org.duckdns.dorandoran.INCOMING_CALL"
        const val ACTION_CALL_HANDLED = "org.duckdns.dorandoran.CALL_HANDLED"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_ROOM_ID = "room_id"
        private const val INCOMING_DEBOUNCE_MS = 1500L
        private var lastIncomingCallId: String? = null
        private var lastIncomingAt: Long = 0L
    }
        private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CALL_HANDLED) {
            stopIncomingNotification()
            return START_STICKY
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupIncomingListener()
        (application as? CallApp)?.callSignalingManager?.startListening()
        // START_STICKY: 시스템이 서비스를 강제 종료했을 때 자동 재시작
        // START_REDELIVER_INTENT: 앱 강제 종료 후 재시작 시 intent 전달
        return START_STICKY or START_REDELIVER_INTENT
    }

    private fun stopIncomingNotification() {
        try {
            startForeground(NOTIFICATION_ID, createNotification())
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID + 1)
            android.util.Log.d("CallListeningService", "Incoming notification cleared")
        } catch (e: Exception) {
            android.util.Log.w("CallListeningService", "Failed to clear incoming notification: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        (application as? CallApp)?.callSignalingManager?.apply {
            // onIncomingCallReceived는 null로 설정하지 않음 (다시 시작될 때 필요)
            stopListening()
        }
        wakeLock?.release()
        super.onDestroy()
    }

    private fun setupIncomingListener() {
        val manager = (application as? CallApp)?.callSignalingManager ?: return
        manager.onIncomingCallReceived = { info: IncomingCallInfo ->
            showIncomingCall(info.callId, info.roomId)
        }
        // 원격에서 hangup 신호 수신 시 알림 취소
        manager.onRemoteHangup = {
            stopIncomingNotification()
            android.util.Log.d("CallListeningService", "Remote hangup received - notification cleared")
        }
        // 통화 종료 시 알림 취소
        manager.onCallEnded = {
            stopIncomingNotification()
        }
    }

    private fun showIncomingCall(callId: String, roomId: String) {
        createIncomingCallChannel()
        val now = System.currentTimeMillis()
        if (lastIncomingCallId == callId && (now - lastIncomingAt) < INCOMING_DEBOUNCE_MS) {
            android.util.Log.d("CallListeningService", "Duplicate incoming ignored: callId=$callId")
            return
        }
        lastIncomingCallId = callId
        lastIncomingAt = now
        android.util.Log.d("CallListeningService", "showIncomingCall: callId=$callId, roomId=$roomId")
        
        // WakeLock 획득 - 기기 화면 켜기
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CallListeningService:IncomingCall"
            ).apply {
                acquire(3000L) // 3초간 유지
            }
        }
        
        val fullScreenIntent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            // API 27+: 잠금 화면 위에 표시
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                addFlags(0x00000800) // FLAG_ACTIVITY_SHOW_WHEN_LOCKED
            }
            action = ACTION_INCOMING_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_ROOM_ID, roomId)
        }
        
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            action = ACTION_INCOMING_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_ROOM_ID, roomId)
        }
        
        val contentPendingIntent = PendingIntent.getActivity(
            this, 1, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 알림 액션 - 받기 버튼
        val acceptIntent = Intent(this, InCallActivity::class.java).apply {
            action = "ACCEPT_CALL"
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_ROOM_ID, roomId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this, callId.hashCode(), acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 알림 액션 - 거절 버튼
        val rejectIntent = Intent(this, InCallActivity::class.java).apply {
            action = "REJECT_CALL"
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_ROOM_ID, roomId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val rejectPendingIntent = PendingIntent.getActivity(
            this, (callId + "reject").hashCode(), rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_INCOMING)
            .setContentTitle("전화가 왔어요")
            .setContentText("수신 전화 - 탭하여 받기")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(false)
            .setTimeoutAfter(60000)
            .setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setLights(-0x10000, 1000, 1000)
            .setStyle(NotificationCompat.BigTextStyle().bigText("수신 전화 - 탭하여 받기"))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColorized(true)
            .setColor(0xFF0099CC.toInt())
            .addAction(
                android.R.drawable.ic_menu_call,
                "받기",
                acceptPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "거절",
                rejectPendingIntent
            )
            .build()
        
        try {
            notificationManager.notify(NOTIFICATION_ID + 1, notification)
            android.util.Log.d("CallListeningService", "Incoming notification posted with ID ${NOTIFICATION_ID + 1}")
        } catch (e: Exception) {
            android.util.Log.w("CallListeningService", "Failed to post incoming notification: ${e.message}")
        }
    }

    private fun createIncomingCallChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID_INCOMING)
            if (existing != null && existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                notificationManager.deleteNotificationChannel(CHANNEL_ID_INCOMING)
            }

            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()
            val vibratePattern = longArrayOf(0, 500, 200, 500, 200, 500)

            val channel = NotificationChannel(
                CHANNEL_ID_INCOMING,
                "수신 전화",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(true)
                enableVibration(true)
                setBypassDnd(true)
                setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, audioAttributes)
                vibrationPattern = vibratePattern
                lightColor = 0xFF0099CC.toInt()
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "수신 전화 대기",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("전화 대기 중")
            .setContentText("수신 전화를 기다리고 있습니다")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
