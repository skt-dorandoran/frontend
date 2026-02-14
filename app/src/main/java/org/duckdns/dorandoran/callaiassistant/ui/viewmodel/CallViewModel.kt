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
    val timestamp: Long = System.currentTimeMillis()
)

class CallViewModel : ViewModel() {
    private val _callInfo = MutableStateFlow(CallInfo())
    val callInfo: StateFlow<CallInfo> = _callInfo.asStateFlow()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        fun clearHistory() {
            _messages.value = emptyList()
        }
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    fun updateCallInfo(phoneNumber: String, hospitalName: String, callTime: String) {
        _callInfo.value = CallInfo(phoneNumber, hospitalName, callTime)
    }
    
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        _messages.value = _messages.value + ChatMessage(text = text, isFromMe = true)
    }
    
    fun addRemoteMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(text = text, isFromMe = false)
    }
}
