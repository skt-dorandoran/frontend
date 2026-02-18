package org.duckdns.dorandoran.callaiassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallInfo(
    val phoneNumber: String = "",
    val hospitalName: String = "보라매 병원",
    val callTime: String = "연결 중..."
)

data class ChatMessage(
    val text: String,
    val isFromMe: Boolean,
    val isStt: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class CallViewModel : ViewModel() {
    private val _callInfo = MutableStateFlow(CallInfo())
    val callInfo: StateFlow<CallInfo> = _callInfo.asStateFlow()
    private val _introPromptPlayed = MutableStateFlow(false)
    val introPromptPlayed: StateFlow<Boolean> = _introPromptPlayed.asStateFlow()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    fun clearHistory() {
        _messages.value = emptyList()
        resetSttTracking()
    }
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var activeSttSpeakerIsMe: Boolean? = null
    private var activeSttMessageIndex: Int? = null
    private var myConsumedRawText: String = ""
    private var remoteConsumedRawText: String = ""
    private var myLastRawText: String = ""
    private var remoteLastRawText: String = ""
    
    fun updateCallInfo(phoneNumber: String, hospitalName: String, callTime: String) {
        _callInfo.value = CallInfo(phoneNumber, hospitalName, callTime)
    }
    
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        finalizeActiveSttSegment()
        _messages.value = _messages.value + ChatMessage(text = text, isFromMe = true)
    }
    
    fun addRemoteMessage(text: String) {
        finalizeActiveSttSegment()
        _messages.value = _messages.value + ChatMessage(text = text, isFromMe = false)
    }

    fun updateMySttMessage(rawText: String) {
        upsertSttMessage(rawText = rawText, isFromMe = true)
    }

    fun updateRemoteSttMessage(rawText: String) {
        upsertSttMessage(rawText = rawText, isFromMe = false)
    }

    fun markIntroPromptPlayed() {
        _introPromptPlayed.value = true
    }

    fun resetIntroPromptPlayed() {
        _introPromptPlayed.value = false
    }

    private fun upsertSttMessage(rawText: String, isFromMe: Boolean) {
        val normalizedRaw = rawText.trim()
        if (normalizedRaw.isBlank()) return

        if (activeSttSpeakerIsMe != null && activeSttSpeakerIsMe != isFromMe) {
            finalizeActiveSttSegment()
        }

        if (activeSttSpeakerIsMe == null) {
            activeSttSpeakerIsMe = isFromMe
            activeSttMessageIndex = null
        }

        val consumed = if (isFromMe) myConsumedRawText else remoteConsumedRawText
        val displayText = subtractConsumedPrefix(normalizedRaw, consumed).trim()

        if (isFromMe) {
            myLastRawText = normalizedRaw
        } else {
            remoteLastRawText = normalizedRaw
        }

        if (displayText.isBlank()) return

        val updated = _messages.value.toMutableList()
        val idx = activeSttMessageIndex
        val canUpdateCurrentBubble = idx != null &&
            idx in updated.indices &&
            updated[idx].isStt &&
            updated[idx].isFromMe == isFromMe

        if (canUpdateCurrentBubble) {
            if (updated[idx].text != displayText) {
                val prev = updated[idx]
                updated[idx] = prev.copy(text = displayText)
                _messages.value = updated
            }
        } else {
            updated += ChatMessage(
                text = displayText,
                isFromMe = isFromMe,
                isStt = true
            )
            activeSttMessageIndex = updated.lastIndex
            _messages.value = updated
        }
    }

    private fun finalizeActiveSttSegment() {
        when (activeSttSpeakerIsMe) {
            true -> myConsumedRawText = myLastRawText
            false -> remoteConsumedRawText = remoteLastRawText
            null -> {}
        }
        activeSttSpeakerIsMe = null
        activeSttMessageIndex = null
    }

    private fun resetSttTracking() {
        activeSttSpeakerIsMe = null
        activeSttMessageIndex = null
        myConsumedRawText = ""
        remoteConsumedRawText = ""
        myLastRawText = ""
        remoteLastRawText = ""
    }

    private fun subtractConsumedPrefix(raw: String, consumed: String): String {
        if (consumed.isBlank()) return raw
        if (raw.startsWith(consumed)) return raw.removePrefix(consumed)

        val lcpLength = longestCommonPrefixLength(raw, consumed)
        return if (lcpLength > 0) raw.substring(lcpLength) else raw
    }

    private fun longestCommonPrefixLength(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }
}
