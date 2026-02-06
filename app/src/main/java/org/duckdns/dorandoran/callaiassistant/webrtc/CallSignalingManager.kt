package org.duckdns.dorandoran.callaiassistant.webrtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * 통화 시그널링 전용 매니저 (수신 대기, incoming/accept/reject)
 * WebRTC PeerConnection은 WebRtcManager가 담당
 */
class CallSignalingManager(private val context: Context) {

    companion object {
        private const val TAG = "CallSignalingManager"
        private const val SIGNALING_URL = "wss://wss.dorandoran.dev"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = OkHttpClient.Builder().build()
    private val webSocketRef = AtomicReference<WebSocket?>(null)

    /** 수신 대기 중인지 */
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    /** 수신 전화 정보 (callId, roomId) - null이면 수신 없음 */
    private val _incomingCall = MutableStateFlow<IncomingCallInfo?>(null)
    val incomingCall: StateFlow<IncomingCallInfo?> = _incomingCall.asStateFlow()

    /** 현재 호출자(caller) 상태 - incoming 메시지 필터링용 */
    private var isCaller = false
    /** 수락 완료된 callId (중복 incoming 무시용) */
    private var acceptedCallId: String? = null
        /** 현재 발신 중인 callId (발신자가 미리 생성해서 보냄) */
        private var currentOutgoingCallId: String? = null

    data class IncomingCallInfo(val callId: String, val roomId: String)

    /** 수신 전화 시 콜백 (서비스에서 전체화면 인텐트용) */
    var onIncomingCallReceived: ((IncomingCallInfo) -> Unit)? = null

    /** 통화 종료 시 콜백 (알림 취소 등) */
    var onCallEnded: (() -> Unit)? = null

    /** 원격에서 hangup 신호 수신 시 콜백 (수신 알림 취소용) */
    var onRemoteHangup: (() -> Unit)? = null

    /** WebRTC 시그널링 메시지 콜백 (offer/answer/ice) - WebRtcManager로 전달 */
    var onSignalingMessage: ((String) -> Unit)? = null

    /**
     * 앱 시작 시 호출 - room 구독하여 수신 대기
     */
    fun startListening() {
        scope.launch {
            withContext(Dispatchers.IO) {
                doStartListening()
            }
        }
    }

    private fun doStartListening() {
        if (webSocketRef.get() != null) {
            Log.d(TAG, "Already listening")
            return
        }
        Log.d(TAG, "Start listening...")
        val request = Request.Builder().url(SIGNALING_URL).build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WS connected (listening)")
                _isListening.value = true
                val subscribeMsg = JSONObject().apply {
                    put("type", "subscribe")
                    put("roomId", WEBRTC_ROOM_ID)
                }.toString()
                Log.d(TAG, "WS -> subscribe: $subscribeMsg")
                webSocket.send(subscribeMsg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch(Dispatchers.Main.immediate) {
                    handleMessage(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WS error: ${t.message}")
                _isListening.value = false
                _incomingCall.value = null
                webSocketRef.set(null)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WS closing")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isListening.value = false
                _incomingCall.value = null
                webSocketRef.set(null)
            }
        })
        webSocketRef.set(ws)
    }

    private fun handleMessage(text: String) {
        try {
            val msg = JSONObject(text)
            val type = msg.optString("type", "")
            Log.d(TAG, "WS <- $type")
            when (type) {
                "incoming" -> {
                    // 호출자인 경우 incoming 메시지 무시 (자신의 호출을 받지 않기 위함)
                    if (isCaller) {
                        Log.d(TAG, "Ignoring incoming call because we are the caller")
                        return
                    }
                    val callId = msg.optString("callId", "")
                    val roomId = msg.optString("roomId", WEBRTC_ROOM_ID)
                    if (acceptedCallId == callId) {
                        Log.d(TAG, "Incoming ignored (already accepted): callId=$callId")
                        return
                    }
                    val current = _incomingCall.value
                    if (current != null) {
                        if (current.callId == callId) {
                            Log.d(TAG, "Duplicate incoming ignored: callId=$callId")
                            return
                        }
                        Log.d(TAG, "Incoming ignored (already has pending call): callId=$callId")
                        return
                    }
                    if (callId.isNotEmpty()) {
                        val info = IncomingCallInfo(callId, roomId)
                        _incomingCall.value = info
                        Log.d(TAG, "Incoming call: callId=$callId, onIncomingCallReceived=${onIncomingCallReceived != null}")
                        onIncomingCallReceived?.invoke(info)
                    } else {
                        Log.w(TAG, "Incoming message with empty callId")
                    }
                }
                "hangup" -> {
                    val callId = msg.optString("callId", "")
                    Log.d(TAG, "hangup received for callId=$callId")
                    val current = _incomingCall.value
                    if (current != null && (callId.isEmpty() || callId == current.callId)) {
                        _incomingCall.value = null
                        onRemoteHangup?.invoke()    // 원격 hangup 콜백 호출
                        onCallEnded?.invoke()       // 통화 종료 콜백 호출
                        Log.d(TAG, "Cleared incoming due to hangup")
                    }
                }
                "peer_left" -> {
                    Log.d(TAG, "peer_left received, clearing incoming state")
                    _incomingCall.value = null
                    acceptedCallId = null
                    onRemoteHangup?.invoke()    // 원격 hangup 콜백 호출
                    onCallEnded?.invoke()       // 통화 종료 콜백 호출
                }
                "offer", "answer", "ice", "callee_joined", "joined", "rejected" -> {
                    // WebRTC 시그널링 메시지를 WebRtcManager로 전달
                    onSignalingMessage?.invoke(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    /**
     * 수신 거절 - 끊기 버튼
     */
    fun rejectCall(callId: String) {
        val ws = webSocketRef.get() ?: return
        ws.send(JSONObject().apply {
            put("type", "reject")
            put("roomId", WEBRTC_ROOM_ID)
            put("callId", callId)
        }.toString())
        _incomingCall.value = null
        acceptedCallId = null
        Log.d(TAG, "Reject sent: $callId")
        
        // 통화 종료 콜백 호출 (알림 취소 등)
        onCallEnded?.invoke()
    }

    /** 수신 수락 - 수신자가 수락할 때 호출. 반드시 listening(구독) 소켓에서 전송되어야 함. */
    fun acceptCall(callId: String) {
        val ws = webSocketRef.get() ?: run {
            Log.w(TAG, "Cannot accept call: not listening (no websocket)")
            return
        }
        acceptedCallId = callId
        ws.send(JSONObject().apply {
            put("type", "accept")
            put("roomId", WEBRTC_ROOM_ID)
            put("callId", callId)
        }.toString())
        Log.d(TAG, "Accept sent: $callId")
        // keep incoming state until caller/callee handshake completes
    }

    /** WebRtcManager에서 시그널링 메시지 전송 (listening 소켓 사용) */
    fun sendSignalingMessage(message: String) {
        val ws = webSocketRef.get()
        if (ws != null) {
            ws.send(message)
            Log.d(TAG, "Signaling message sent via listening socket")
        } else {
            Log.w(TAG, "Cannot send signaling: no websocket")
        }
    }

    /**
     * 수신 대기 종료 (기존 WebSocket 닫기, WebRtcManager가 새로 연결할 때 사용)
     */
    fun stopListening() {
        webSocketRef.get()?.close(1000, "stop")
        webSocketRef.set(null)
        _isListening.value = false
        _incomingCall.value = null
        Log.d(TAG, "Stop listening")
    }

    /**
     * 원격 hangup 수신 - 수신 상태 취소 및 콜백 호출
     * (B가 수신 알림 상태일 때 A가 hangup을 보낸 경우 처리)
     */
    fun handleRemoteHangup() {
        _incomingCall.value = null
        acceptedCallId = null
        onRemoteHangup?.invoke()
        onCallEnded?.invoke()
        Log.d(TAG, "Remote hangup handled - incoming call cleared")
    }

    /**
     * 호출자 모드 시작 (incoming 메시지 필터링)
     */
    fun markAsCaller() {
        isCaller = true
        Log.d(TAG, "Marked as caller - will ignore incoming messages")
    }

    /**
     * 호출자: 현재 열려있는 listening WebSocket을 통해 room에 'call'을 보냄
     * 서버는 이 메시지를 받고 call을 생성하여 다른 참가자에게 알림을 보낼 것입니다.
     */
    fun initiateCall(): String {
        val ws = webSocketRef.get()
        if (ws == null) {
            Log.w(TAG, "Cannot initiate call: not listening (no websocket)")
            return ""
        }
        val newCallId = java.util.UUID.randomUUID().toString()
        currentOutgoingCallId = newCallId
        val callMsg = JSONObject().apply {
            put("type", "call")
            put("roomId", WEBRTC_ROOM_ID)
            put("callId", newCallId)
        }.toString()
        Log.d(TAG, "WS -> call: $callMsg")
        ws.send(callMsg)
        Log.d(TAG, "Call initiated via signaling socket - callId=$newCallId")
        return newCallId
    }

    /**
     * 호출자 모드 종료
     */
    fun clearCallerMode() {
        isCaller = false
        currentOutgoingCallId = null
        Log.d(TAG, "Cleared caller mode")
    }

    /**
     * 수신 화면 닫기 (incoming 상태만 초기화)
     */
    fun clearIncoming() {
        _incomingCall.value = null
        acceptedCallId = null
    }

    /**
     * 인텐트로부터 수신 전화 설정 (전체화면 인텐트에서 호출)
     */
    fun setIncomingFromIntent(info: IncomingCallInfo) {
        _incomingCall.value = info
    }
}
