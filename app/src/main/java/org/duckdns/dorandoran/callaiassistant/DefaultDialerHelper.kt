package org.duckdns.dorandoran.callaiassistant

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log

object DefaultDialerHelper {

    private const val TAG = "DefaultDialerHelper"

    /**
     * 기본 전화 앱으로 설정 가능한지 확인
     */
    fun isRoleAvailable(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
        } else {
            false
        }
    }

    /**
     * 이미 기본 전화 앱인지 확인
     */
    fun isDefaultDialer(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.defaultDialerPackage == context.packageName
            }
            else -> false
        }
    }

    /**
     * 기본 전화 앱 설정 요청 (시스템 다이얼로그 표시)
     * @param roleRequestLauncher RoleManager 인텐트용 startActivityForResult 런처 (API 29+)
     */
    fun requestDefaultDialer(activity: Activity, roleRequestLauncher: ((Intent) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                return true
            }
        }

        // 1) RoleManager (API 29+) - startActivityForResult 필수, 다이얼로그 표시
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleRequestLauncher != null) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            if (intent != null) {
                try {
                    roleRequestLauncher(intent)
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "RoleManager launch failed: ${e.message}", e)
                }
            }
        }

        // 2) TelecomManager 폴백 (API 23+, 일부 기기에서 동작)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomIntent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
            }
            if (tryStartActivity(activity, telecomIntent)) {
                return true
            }
        }

        // 3) 최종 폴백: 설정 화면
        openDefaultAppsSettings(activity)
        return true
    }

    private fun tryStartActivity(activity: Activity, intent: Intent): Boolean {
        return try {
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "startActivity failed: ${e.message}", e)
            false
        }
    }

    /**
     * 기본 앱 설정 화면 열기 (직접 호출 가능)
     */
    fun openDefaultAppsSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open default apps settings", e)
        }
    }
}
