package org.duckdns.dorandoran.callaiassistant

import android.content.Context
import android.content.Intent
import android.net.Uri

object PhoneCallHelper {

    /**
     * 전화 걸기
     * @param context 컨텍스트
     * @param phoneNumber 전화번호 (숫자만)
     * @return 전화 앱으로 인텐트 전달 성공 여부
     */
    fun makeCall(context: Context, phoneNumber: String): Boolean {
        val cleanedNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        if (cleanedNumber.isBlank()) return false

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$cleanedNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
