package org.duckdns.dorandoran.callaiassistant.data

import java.util.Date

/**
 * 전화 기록 항목
 */
data class CallLogItem(
    val id: Long,
    val phoneNumber: String,
    val contactName: String?,
    val callType: CallType,
    val date: Date,
    val duration: Long
) {
    val displayName: String
        get() = contactName?.takeIf { it.isNotBlank() } ?: phoneNumber.ifBlank { "알 수 없는 번호" }
}

enum class CallType {
    INCOMING,   // 수신
    OUTGOING,   // 발신
    MISSED      // 부재중
}
