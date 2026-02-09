package org.duckdns.dorandoran.callaiassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager

object PhoneCallHelper {

    /**
     * TelecomManager.placeCall()로 전화 걸기
     * 기본 전화 앱으로 설정된 경우 우리 앱의 InCallService에서 통화 UI 표시
     * 실패 시 ACTION_CALL으로 폴백
     */
    fun makeCall(context: Context, phoneNumber: String): Boolean {
        val cleanedNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        if (cleanedNumber.isBlank()) return false

        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val uri = Uri.fromParts("tel", cleanedNumber, null)
            telecomManager.placeCall(uri, null)
            true
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$cleanedNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e2: SecurityException) {
                false
            }
        }
    }

    /**
     * 통화 종료 (기본 전화 앱인 경우 동작)
     */
    fun endCall(context: Context): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.endCall()
        } catch (e: Exception) {
            false
        }
    }
}
