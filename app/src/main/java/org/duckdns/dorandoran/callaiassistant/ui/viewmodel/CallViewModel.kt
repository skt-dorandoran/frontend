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
import org.duckdns.dorandoran.callaiassistant.stt.RealtimeSttPayload
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
    TEXT_MODE,
    AI_SUGGESTION
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

data class SilenceInterventionUiState(
    val eventId: Long = 0L,
    val silenceDurationSeconds: Double = 0.0,
    val interventionText: String = "",
    val visible: Boolean = false
)

class CallViewModel : ViewModel() {
    companion object {
        private const val TAG = "CallViewModel"
        private val NOISE_ONLY_REGEX = Regex("^[\\p{Punct}\\s·…]+$")
    }

    private data class SttSpeakerState(
        val finalChunks: MutableMap<Long, String> = mutableMapOf(),
        var currentStartKey: Long? = null,
        var currentText: String = "",
        var activeBubbleIndex: Int? = null,
        var syntheticStartKey: Long = -1L
    )

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
    private val _silenceIntervention = MutableStateFlow(SilenceInterventionUiState())
    val silenceIntervention: StateFlow<SilenceInterventionUiState> = _silenceIntervention.asStateFlow()
    private val _aiCorrectionAlert = MutableStateFlow(false)
    val aiCorrectionAlert: StateFlow<Boolean> = _aiCorrectionAlert.asStateFlow()
    private var activeSessionId: Long = 0L
    private var activeSessionKey: String = ""

    fun clearHistory() {
        _messages.value = emptyList()
        _aiCorrectionDraftText.value = ""
        _aiCorrectionRecording.value = false
        resetAiCorrectionDraftState()
        _aiSuggestionTop1.value = "여보세요"
        _aiSuggestionTop2.value = "안녕하세요"
        _isRefreshingAiSuggestions.value = false
        _silenceIntervention.value = SilenceInterventionUiState()
        _aiCorrectionAlert.value = false
        refreshLastConversationBubbles()
        resetSttTracking()
        syncConversationHistory()
    }
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val mySttState = SttSpeakerState()
    private val remoteSttState = SttSpeakerState()
    private var activeTurnSpeakerIsMe: Boolean? = null
    private val aiCorrectionCommittedChunks: MutableMap<Long, String> = mutableMapOf()
    private var aiCorrectionCurrentStartKey: Long? = null
    private var aiCorrectionCurrentText: String = ""
    private var aiCorrectionSyntheticStartKey: Long = -1L
    private var aiCorrectionIgnoreEventsUntilMs: Long = 0L
    
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

    fun updateMySttMessage(payload: RealtimeSttPayload, isFinal: Boolean) {
        if (payload.text.trim().isNotBlank()) {
            dismissSilenceIntervention()
        }
        if (_aiCorrectionRecording.value) {
            updateAiCorrectionDraft(payload, isFinal)
            return
        }
        upsertSttMessage(payload = payload, isFromMe = true, isFinal = isFinal)
    }

    fun updateRemoteSttMessage(payload: RealtimeSttPayload, isFinal: Boolean) {
        upsertSttMessage(payload = payload, isFromMe = false, isFinal = isFinal)
    }

    fun markIntroPromptPlayed() {
        _introPromptPlayed.value = true
    }

    fun resetIntroPromptPlayed() {
        _introPromptPlayed.value = false
    }

    fun onSilenceDetected(silenceDurationSeconds: Double) {
        _silenceIntervention.value = SilenceInterventionUiState(
            eventId = System.currentTimeMillis(),
            silenceDurationSeconds = silenceDurationSeconds,
            interventionText = "잠시만요",
            visible = true
        )
    }

    fun dismissSilenceIntervention() {
        if (!_silenceIntervention.value.visible) return
        _silenceIntervention.value = _silenceIntervention.value.copy(visible = false)
    }

    fun onComprehensionAlert() {
        _aiCorrectionAlert.value = true
    }

    fun consumeComprehensionAlert() {
        _aiCorrectionAlert.value = false
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
        resetAiCorrectionDraftState()
        // 재시작 직후 이전 세션 잔여 이벤트가 도착하는 경우를 완화한다.
        aiCorrectionIgnoreEventsUntilMs = System.currentTimeMillis() + 300L
    }

    fun stopAiCorrectionRecording() {
        _aiCorrectionRecording.value = false
    }

    fun clearAiCorrectionDraft() {
        _aiCorrectionDraftText.value = ""
        resetAiCorrectionDraftState()
    }

    private fun upsertSttMessage(payload: RealtimeSttPayload, isFromMe: Boolean, isFinal: Boolean) {
        val displayText = sanitizeSttDisplayText(payload.text).trim()
        if (displayText.isBlank()) return

        // 화자가 바뀌면 이전 화자의 현재 말풍선을 그 시점까지 확정하고,
        // 새 화자는 다음 말풍선부터 시작한다.
        if (activeTurnSpeakerIsMe != null && activeTurnSpeakerIsMe != isFromMe) {
            finishSpeakerTurn(if (activeTurnSpeakerIsMe == true) mySttState else remoteSttState)
            activeTurnSpeakerIsMe = isFromMe
        } else if (activeTurnSpeakerIsMe == null) {
            activeTurnSpeakerIsMe = isFromMe
        }

        val state = if (isFromMe) mySttState else remoteSttState
        val startKey = resolveSpeakerStartKey(payload, state, isFinal)

        if (isFinal) {
            state.finalChunks[startKey] = displayText
            if (state.currentStartKey == startKey) {
                state.currentStartKey = null
                state.currentText = ""
            }
        } else {
            state.currentStartKey = startKey
            state.currentText = displayText
        }

        val merged = buildSpeakerTurnText(state)
        if (merged.isBlank()) return

        val bubbleIndex = state.activeBubbleIndex
        if (bubbleIndex != null && isValidSttBubbleIndex(bubbleIndex, isFromMe)) {
            updateBubbleText(bubbleIndex, merged)
        } else {
            state.activeBubbleIndex = appendSttBubble(merged, isFromMe)
        }

        if (payload.speechFinal) {
            finishSpeakerTurn(state)
            if (activeTurnSpeakerIsMe == isFromMe) {
                activeTurnSpeakerIsMe = null
            }
        }
    }

    private fun updateAiCorrectionDraft(payload: RealtimeSttPayload, isFinal: Boolean) {
        if (System.currentTimeMillis() < aiCorrectionIgnoreEventsUntilMs) return

        val normalizedText = sanitizeSttDisplayText(payload.text).trim()
        if (normalizedText.isBlank()) return

        val startKey = resolveAiCorrectionStartKey(payload, isFinal)
        if (isFinal || payload.speechFinal) {
            aiCorrectionCommittedChunks[startKey] = normalizedText
            // HTML 테스트 페이지와 동일하게 final이 오면 interim 표시를 즉시 비운다.
            aiCorrectionCurrentStartKey = null
            aiCorrectionCurrentText = ""
        } else {
            aiCorrectionCurrentStartKey = startKey
            aiCorrectionCurrentText = normalizedText
        }

        renderAiCorrectionDraftText()
    }

    private fun resetAiCorrectionDraftState() {
        aiCorrectionCommittedChunks.clear()
        aiCorrectionCurrentStartKey = null
        aiCorrectionCurrentText = ""
        aiCorrectionSyntheticStartKey = -1L
    }

    private fun renderAiCorrectionDraftText() {
        val committed = aiCorrectionCommittedChunks.keys
            .sorted()
            .mapNotNull { aiCorrectionCommittedChunks[it] }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val current = aiCorrectionCurrentText.trim()
        _aiCorrectionDraftText.value = when {
            current.isBlank() -> committed
            committed.isBlank() -> current
            else -> "$committed $current".replace(Regex("\\s+"), " ").trim()
        }
    }

    private fun resolveAiCorrectionStartKey(payload: RealtimeSttPayload, isFinal: Boolean): Long {
        val start = payload.start
        if (start != null && !start.isNaN()) {
            return kotlin.math.round(start * 1000.0).toLong()
        }
        if (aiCorrectionCurrentStartKey != null) {
            return aiCorrectionCurrentStartKey!!
        }
        return aiCorrectionSyntheticStartKey--
    }

    private fun finalizeActiveSttSegment() {
        finishSpeakerTurn(mySttState)
        finishSpeakerTurn(remoteSttState)
        activeTurnSpeakerIsMe = null
    }

    private fun resetSttTracking() {
        resetSpeakerState(mySttState)
        resetSpeakerState(remoteSttState)
        activeTurnSpeakerIsMe = null
    }

    private fun resolveSpeakerStartKey(
        payload: RealtimeSttPayload,
        state: SttSpeakerState,
        isFinal: Boolean
    ): Long {
        val start = payload.start
        if (start != null && !start.isNaN()) {
            return kotlin.math.round(start * 1000.0).toLong()
        }
        if (state.currentStartKey != null) {
            return state.currentStartKey!!
        }
        return state.syntheticStartKey--
    }

    private fun buildSpeakerTurnText(state: SttSpeakerState): String {
        val committed = state.finalChunks.keys
            .sorted()
            .mapNotNull { state.finalChunks[it] }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val current = state.currentText.trim()
        return when {
            current.isBlank() -> committed
            committed.isBlank() -> current
            else -> "$committed $current".replace(Regex("\\s+"), " ").trim()
        }
    }

    private fun finishSpeakerTurn(state: SttSpeakerState) {
        state.finalChunks.clear()
        state.currentStartKey = null
        state.currentText = ""
        state.activeBubbleIndex = null
        state.syntheticStartKey = -1L
    }

    private fun resetSpeakerState(state: SttSpeakerState) {
        finishSpeakerTurn(state)
    }

    private fun isValidSttBubbleIndex(index: Int, isFromMe: Boolean): Boolean {
        val current = _messages.value
        if (index !in current.indices) return false
        val message = current[index]
        return message.isStt && message.isFromMe == isFromMe
    }

    private fun appendSttBubble(text: String, isFromMe: Boolean): Int {
        val updated = _messages.value.toMutableList()
        updated += ChatMessage(
            text = text,
            isFromMe = isFromMe,
            isStt = true
        )
        _messages.value = updated
        refreshLastConversationBubbles()
        syncConversationHistory()
        return updated.lastIndex
    }

    private fun updateBubbleText(index: Int, text: String) {
        val updated = _messages.value.toMutableList()
        if (index !in updated.indices) return
        val previous = updated[index]
        if (previous.text == text) return
        updated[index] = previous.copy(text = text)
        _messages.value = updated
        refreshLastConversationBubbles()
        syncConversationHistory()
    }

    /**
     * Streaming STT 재작성 과정에서 생기는 근접 중복 구문을 완화한다.
     * 예) "제 이름은 한 조용 제 이름은 한지용이라고 합니다"
     *  -> "제 이름은 한지용이라고 합니다"
     */
    private fun sanitizeSttDisplayText(raw: String): String {
        var compact = raw.replace(Regex("\\s+"), " ").trim()
        if (compact.isBlank()) return compact

        var tokens = compact.split(' ').toMutableList()
        if (tokens.size < 4) return compact

        // 1) 인접 반복 n-gram 제거: "안녕하세요 안녕하세요", "제 이름은 제 이름은"
        var changed = true
        while (changed && tokens.size >= 4) {
            changed = false
            loop@ for (n in 5 downTo 2) {
                if (tokens.size < n * 2) continue
                for (i in 0..(tokens.size - n * 2)) {
                    val first = tokens.subList(i, i + n)
                    val second = tokens.subList(i + n, i + n * 2)
                    if (first == second) {
                        repeat(n) { tokens.removeAt(i) } // 앞 반복 제거, 최신 가설 유지
                        changed = true
                        break@loop
                    }
                }
            }
        }

        compact = tokens.joinToString(" ").trim()
        if (compact.isBlank()) return compact
        tokens = compact.split(' ').toMutableList()
        if (tokens.size < 6) return compact

        var bestStart = -1
        var bestRepeatStart = -1
        var bestLen = 0

        for (start in 0 until tokens.size - 3) {
            // 너무 멀리 떨어진 반복은 실제 재언급일 수 있어 제한한다.
            val maxRepeatStart = minOf(tokens.lastIndex, start + 14)
            for (repeatStart in (start + 2)..maxRepeatStart) {
                for (len in 5 downTo 2) {
                    if (start + len > tokens.size || repeatStart + len > tokens.size) continue
                    val first = tokens.subList(start, start + len)
                    val second = tokens.subList(repeatStart, repeatStart + len)
                    if (first == second) {
                        if (len > bestLen || (len == bestLen && repeatStart > bestRepeatStart)) {
                            bestStart = start
                            bestRepeatStart = repeatStart
                            bestLen = len
                        }
                        break
                    }
                }
            }
        }

        if (bestStart >= 0 && bestRepeatStart > bestStart) {
            val deduped = buildList {
                addAll(tokens.subList(0, bestStart))
                addAll(tokens.subList(bestRepeatStart, tokens.size))
            }.joinToString(" ")
            return deduped.replace(Regex("\\s+"), " ").trim()
        }

        return compact
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
