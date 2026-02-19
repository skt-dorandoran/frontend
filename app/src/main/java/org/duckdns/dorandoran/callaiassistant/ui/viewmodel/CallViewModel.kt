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
    val origin: MessageOrigin = MessageOrigin.GENERAL,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageOrigin {
    GENERAL,
    TEXT_MODE
}

enum class ConversationSpeaker {
    ME, REMOTE
}

enum class ConversationSource {
    STT, TYPED
}

data class ConversationUtterance(
    val text: String,
    val speaker: ConversationSpeaker,
    val source: ConversationSource,
    val timestamp: Long
)

data class ConversationHistory(
    val sessionId: Long = 0L,
    val sessionKey: String = "",
    val startedAtMs: Long = 0L,
    val utterances: List<ConversationUtterance> = emptyList()
)

class CallViewModel : ViewModel() {
    companion object {
        private const val STT_BUBBLE_MERGE_WINDOW_MS = 800L
    }

    private val _callInfo = MutableStateFlow(CallInfo())
    val callInfo: StateFlow<CallInfo> = _callInfo.asStateFlow()
    private val _introPromptPlayed = MutableStateFlow(false)
    val introPromptPlayed: StateFlow<Boolean> = _introPromptPlayed.asStateFlow()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _textModeLastMyBubble = MutableStateFlow("")
    val textModeLastMyBubble: StateFlow<String> = _textModeLastMyBubble.asStateFlow()
    private val _textModeLastRemoteBubble = MutableStateFlow("")
    val textModeLastRemoteBubble: StateFlow<String> = _textModeLastRemoteBubble.asStateFlow()
    private val _conversationHistory = MutableStateFlow(ConversationHistory())
    val conversationHistory: StateFlow<ConversationHistory> = _conversationHistory.asStateFlow()
    private var activeSessionId: Long = 0L
    private var activeSessionKey: String = ""

    fun clearHistory() {
        _messages.value = emptyList()
        refreshLastConversationBubbles()
        resetSttTracking()
        syncConversationHistory()
    }
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var activeSttSpeakerIsMe: Boolean? = null
    private var activeSttMessageIndex: Int? = null
    private var myConsumedRawText: String = ""
    private var remoteConsumedRawText: String = ""
    private var myLastRawText: String = ""
    private var remoteLastRawText: String = ""
    private var lastMySttUpdateAtMs: Long = 0L
    private var lastRemoteSttUpdateAtMs: Long = 0L
    
    fun updateCallInfo(phoneNumber: String, hospitalName: String, callTime: String) {
        _callInfo.value = CallInfo(phoneNumber, hospitalName, callTime)
    }

    fun startNewConversationSession(sessionKey: String) {
        val normalized = sessionKey.trim()
        if (normalized.isBlank()) return
        activeSessionId = System.currentTimeMillis()
        activeSessionKey = normalized
        clearHistory()
        _conversationHistory.value = ConversationHistory(
            sessionId = activeSessionId,
            sessionKey = activeSessionKey,
            startedAtMs = activeSessionId,
            utterances = emptyList()
        )
    }

    fun endConversationSession() {
        finalizeActiveSttSegment()
        activeSessionKey = ""
    }

    fun getRecentUtterances(limit: Int = 8): List<ConversationUtterance> {
        if (limit <= 0) return emptyList()
        return _conversationHistory.value.utterances.takeLast(limit)
    }

    fun getRecentConversationText(limit: Int = 8): String {
        return getRecentUtterances(limit)
            .joinToString(separator = "\n") { utterance ->
                val speaker = if (utterance.speaker == ConversationSpeaker.ME) "ME" else "REMOTE"
                "[$speaker] ${utterance.text}"
            }
    }
    
    fun sendMessage(text: String, origin: MessageOrigin = MessageOrigin.GENERAL) {
        if (text.isBlank()) return
        finalizeActiveSttSegment()
        _messages.value = _messages.value + ChatMessage(
            text = text,
            isFromMe = true,
            origin = origin
        )
        refreshLastConversationBubbles()
        syncConversationHistory()
    }
    
    fun addRemoteMessage(text: String, origin: MessageOrigin = MessageOrigin.GENERAL) {
        finalizeActiveSttSegment()
        _messages.value = _messages.value + ChatMessage(
            text = text,
            isFromMe = false,
            origin = origin
        )
        refreshLastConversationBubbles()
        syncConversationHistory()
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

    fun updateTextModeLastBubbles(myText: String, remoteText: String) {
        _textModeLastMyBubble.value = myText.trim()
        _textModeLastRemoteBubble.value = remoteText.trim()
    }

    private fun upsertSttMessage(rawText: String, isFromMe: Boolean) {
        val now = System.currentTimeMillis()
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
        var idx = activeSttMessageIndex

        // If active segment was reset but same-speaker STT resumed shortly,
        // merge back into the last STT bubble to avoid sentence fragmentation.
        if (idx == null && updated.isNotEmpty()) {
            val last = updated.last()
            val withinMergeWindow = if (isFromMe) {
                now - lastMySttUpdateAtMs <= STT_BUBBLE_MERGE_WINDOW_MS
            } else {
                now - lastRemoteSttUpdateAtMs <= STT_BUBBLE_MERGE_WINDOW_MS
            }
            if (last.isStt && last.isFromMe == isFromMe && withinMergeWindow) {
                idx = updated.lastIndex
                activeSttMessageIndex = idx
            }
        }

        val canUpdateCurrentBubble = idx != null &&
            idx in updated.indices &&
            updated[idx].isStt &&
            updated[idx].isFromMe == isFromMe

        if (canUpdateCurrentBubble) {
            if (updated[idx].text != displayText) {
                val prev = updated[idx]
                updated[idx] = prev.copy(text = displayText)
                _messages.value = updated
                refreshLastConversationBubbles()
                syncConversationHistory()
            }
        } else {
            updated += ChatMessage(
                text = displayText,
                isFromMe = isFromMe,
                isStt = true
            )
            activeSttMessageIndex = updated.lastIndex
            _messages.value = updated
            refreshLastConversationBubbles()
            syncConversationHistory()
        }

        if (isFromMe) {
            lastMySttUpdateAtMs = now
        } else {
            lastRemoteSttUpdateAtMs = now
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
        lastMySttUpdateAtMs = 0L
        lastRemoteSttUpdateAtMs = 0L
    }

    private fun subtractConsumedPrefix(raw: String, consumed: String): String {
        if (consumed.isBlank()) return raw
        if (raw.startsWith(consumed)) return raw.removePrefix(consumed)
        return raw
    }

    private fun syncConversationHistory() {
        val utterances = _messages.value.map { message ->
            ConversationUtterance(
                text = message.text,
                speaker = if (message.isFromMe) ConversationSpeaker.ME else ConversationSpeaker.REMOTE,
                source = if (message.isStt) ConversationSource.STT else ConversationSource.TYPED,
                timestamp = message.timestamp
            )
        }
        val current = _conversationHistory.value
        _conversationHistory.value = current.copy(
            sessionId = if (activeSessionId != 0L) activeSessionId else current.sessionId,
            sessionKey = if (activeSessionKey.isNotBlank()) activeSessionKey else current.sessionKey,
            utterances = utterances
        )
    }

    private fun refreshLastConversationBubbles() {
        val current = _messages.value
        _textModeLastMyBubble.value = current.lastOrNull { it.isFromMe }?.text.orEmpty()
        _textModeLastRemoteBubble.value = current.lastOrNull { !it.isFromMe }?.text.orEmpty()
    }
}
