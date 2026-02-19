package org.duckdns.dorandoran.callaiassistant.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.duckdns.dorandoran.callaiassistant.SettingsStore
import org.duckdns.dorandoran.callaiassistant.ai.AiSuggestionApi
import java.util.UUID

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

data class ConversationHistoryItem(
    val role: String,
    val text: String
)

class CallViewModel : ViewModel() {
    companion object {
        private const val TAG = "CallViewModel"
        private const val STT_SEGMENT_SPLIT_GAP_MS = 1300L
        private val NOISE_ONLY_REGEX = Regex("^[\\p{Punct}\\s·…]+$")
        private val SENTENCE_END_REGEX = Regex("[.!?…。？！]$")
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
    private val _aiCorrectionDraftText = MutableStateFlow("")
    val aiCorrectionDraftText: StateFlow<String> = _aiCorrectionDraftText.asStateFlow()
    private val _aiCorrectionRecording = MutableStateFlow(false)
    val aiCorrectionRecording: StateFlow<Boolean> = _aiCorrectionRecording.asStateFlow()
    private val _conversationHistory = MutableStateFlow(ConversationHistory())
    val conversationHistory: StateFlow<ConversationHistory> = _conversationHistory.asStateFlow()
    private val _aiSuggestionTop1 = MutableStateFlow("여보세요")
    val aiSuggestionTop1: StateFlow<String> = _aiSuggestionTop1.asStateFlow()
    private val _aiSuggestionTop2 = MutableStateFlow("안녕하세요")
    val aiSuggestionTop2: StateFlow<String> = _aiSuggestionTop2.asStateFlow()
    private val _isRefreshingAiSuggestions = MutableStateFlow(false)
    val isRefreshingAiSuggestions: StateFlow<Boolean> = _isRefreshingAiSuggestions.asStateFlow()
    private var activeSessionId: Long = 0L
    private var activeSessionKey: String = ""

    fun clearHistory() {
        _messages.value = emptyList()
        _aiCorrectionDraftText.value = ""
        _aiCorrectionRecording.value = false
        aiCorrectionCommittedText = ""
        aiCorrectionActiveSegmentText = ""
        _aiSuggestionTop1.value = "여보세요"
        _aiSuggestionTop2.value = "안녕하세요"
        _isRefreshingAiSuggestions.value = false
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
    private var aiCorrectionCommittedText: String = ""
    private var aiCorrectionActiveSegmentText: String = ""
    
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
        if (_aiCorrectionRecording.value) {
            updateAiCorrectionDraft(rawText)
            return
        }
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

    fun refreshAiSuggestions(context: Context) {
        if (_isRefreshingAiSuggestions.value) return
        _isRefreshingAiSuggestions.value = true
        val appContext = context.applicationContext

        viewModelScope.launch {
            try {
                val recent = getRecentUtterances(limit = 4)
                    .filter { isMeaningfulText(it.text) }
                val history = recent.map {
                    ConversationHistoryItem(
                        role = if (it.speaker == ConversationSpeaker.ME) "user" else "other",
                        text = it.text.trim()
                    )
                }
                val userSpeech = recent.asReversed()
                    .firstOrNull { it.speaker == ConversationSpeaker.REMOTE && isMeaningfulText(it.text) }
                    ?.text
                    ?.trim()
                    ?: recent.lastOrNull()?.text?.trim().orEmpty()
                val phoneNumber = SettingsStore.getMyPhoneNumber(appContext).ifBlank {
                    _callInfo.value.phoneNumber
                }

                val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                val result = AiSuggestionApi.generateResponse(
                    callId = callId,
                    userSpeech = userSpeech,
                    conversationHistory = history,
                    phoneNumber = phoneNumber
                )
                val top2 = result?.answers
                    ?.map { it.text.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.take(2)
                    .orEmpty()
                if (top2.size >= 2) {
                    _aiSuggestionTop1.value = top2[0]
                    _aiSuggestionTop2.value = top2[1]
                    Log.d(TAG, "AI suggestions updated: top1='${top2[0]}', top2='${top2[1]}'")
                } else if (top2.size == 1) {
                    _aiSuggestionTop1.value = top2[0]
                    Log.d(TAG, "AI suggestion updated: top1='${top2[0]}'")
                } else {
                    Log.w(TAG, "AI suggestions parse result empty; keeping previous suggestions")
                }
            } finally {
                _isRefreshingAiSuggestions.value = false
            }
        }
    }

    fun startAiCorrectionRecording() {
        finalizeActiveSttSegment()
        _aiCorrectionDraftText.value = ""
        _aiCorrectionRecording.value = true
        aiCorrectionCommittedText = ""
        aiCorrectionActiveSegmentText = ""
    }

    fun stopAiCorrectionRecording() {
        _aiCorrectionRecording.value = false
    }

    fun clearAiCorrectionDraft() {
        _aiCorrectionDraftText.value = ""
        aiCorrectionCommittedText = ""
        aiCorrectionActiveSegmentText = ""
    }

    private fun upsertSttMessage(rawText: String, isFromMe: Boolean) {
        val now = System.currentTimeMillis()
        val normalizedRaw = rawText.trim()
        if (normalizedRaw.isBlank()) return

        val lastUpdateAtMs = if (isFromMe) lastMySttUpdateAtMs else lastRemoteSttUpdateAtMs
        if (activeSttSpeakerIsMe == isFromMe && lastUpdateAtMs > 0L && now - lastUpdateAtMs > STT_SEGMENT_SPLIT_GAP_MS) {
            finalizeActiveSttSegment()
        }

        if (activeSttSpeakerIsMe != null && activeSttSpeakerIsMe != isFromMe) {
            finalizeActiveSttSegment()
        }

        if (activeSttSpeakerIsMe == null) {
            activeSttSpeakerIsMe = isFromMe
            activeSttMessageIndex = null
        }

        val consumed = if (isFromMe) myConsumedRawText else remoteConsumedRawText
        val displayText = subtractConsumedPrefixSmart(normalizedRaw, consumed).trim()

        if (isFromMe) {
            myLastRawText = normalizedRaw
        } else {
            remoteLastRawText = normalizedRaw
        }

        if (displayText.isBlank()) return

        val updated = _messages.value.toMutableList()
        var idx = activeSttMessageIndex

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

        // 문장 끝 표식이 잡히면 현재 STT 세그먼트를 닫아 다음 문장을 새 말풍선으로 시작한다.
        if (SENTENCE_END_REGEX.containsMatchIn(displayText)) {
            finalizeActiveSttSegment()
        }
    }

    private fun updateAiCorrectionDraft(rawText: String) {
        val normalizedRaw = rawText.trim()
        if (normalizedRaw.isBlank()) return
        myLastRawText = normalizedRaw

        if (aiCorrectionActiveSegmentText.isBlank()) {
            aiCorrectionActiveSegmentText = normalizedRaw
        } else {
            val sameSegment =
                normalizedRaw.startsWith(aiCorrectionActiveSegmentText) ||
                    aiCorrectionActiveSegmentText.startsWith(normalizedRaw)
            if (sameSegment) {
                aiCorrectionActiveSegmentText = normalizedRaw
            } else {
                aiCorrectionCommittedText = appendWithSpace(
                    aiCorrectionCommittedText,
                    aiCorrectionActiveSegmentText
                )
                aiCorrectionActiveSegmentText = normalizedRaw
            }
        }

        _aiCorrectionDraftText.value = buildString {
            if (aiCorrectionCommittedText.isNotBlank()) {
                append(aiCorrectionCommittedText)
            }
            if (aiCorrectionActiveSegmentText.isNotBlank()) {
                if (isNotEmpty()) append(' ')
                append(aiCorrectionActiveSegmentText)
            }
        }.trim()
    }

    private fun appendWithSpace(base: String, added: String): String {
        if (added.isBlank()) return base
        if (base.isBlank()) return added.trim()
        return "$base ${added.trim()}"
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

    private fun subtractConsumedPrefixSmart(raw: String, consumed: String): String {
        val direct = subtractConsumedPrefix(raw, consumed)
        if (direct != raw) return direct
        if (consumed.isBlank()) return raw

        val normalizedConsumed = consumed.trim()
        val normalizedRaw = raw.trim()
        if (normalizedConsumed.isBlank() || normalizedRaw.isBlank()) return raw

        val maxOverlap = minOf(normalizedConsumed.length, normalizedRaw.length)
        for (len in maxOverlap downTo 2) {
            val suffix = normalizedConsumed.takeLast(len)
            if (normalizedRaw.startsWith(suffix)) {
                return normalizedRaw.removePrefix(suffix).trimStart()
            }
        }
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
        _textModeLastMyBubble.value = current.asReversed()
            .firstOrNull { it.isFromMe && isMeaningfulText(it.text) }
            ?.text
            .orEmpty()
        _textModeLastRemoteBubble.value = current.asReversed()
            .firstOrNull { !it.isFromMe && isMeaningfulText(it.text) }
            ?.text
            .orEmpty()
    }

    private fun isMeaningfulText(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) return false
        if (normalized.length == 1 && !normalized[0].isLetterOrDigit()) return false
        if (NOISE_ONLY_REGEX.matches(normalized)) return false
        return true
    }
}
