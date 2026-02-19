package org.duckdns.dorandoran.callaiassistant.webrtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
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
import org.webrtc.AudioSink
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription

// 상대방 오디오 STT 연동용
private var remoteSttManager: org.duckdns.dorandoran.callaiassistant.stt.SherpaOnnxSttManager? = null
private var remoteSttStream: com.k2fsa.sherpa.onnx.OnlineStream? = null
private var remoteAudioSink: AudioSink? = null
private var remoteAudioSinkAttachedTrack: AudioTrack? = null
private var remoteSttHpPrevIn: Float = 0f
private var remoteSttHpPrevOut: Float = 0f
private var remoteSttAgcGain: Float = 1f

/** 전역 고정 방 ID - 사용자 변경 불가 */
const val WEBRTC_ROOM_ID = "dorandoran-room"

/**
 * WebRTC + coturn 시그널링 및 PeerConnection 관리
 * 참조: wss://wss.dorandoran.dev, STUN/TURN dorandoran.dev:3478
 */
class WebRtcManager(private val context: Context, private val signalingManager: CallSignalingManager? = null) {

    companion object {
        private const val TAG = "WebRtcManager"
        private const val SIGNALING_URL = "wss://wss.dorandoran.dev"

        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:dorandoran.dev:3478").createIceServer(),
            PeerConnection.IceServer.builder("turn:dorandoran.dev:3478?transport=udp")
                .setUsername("webrtc")
                .setPassword("dorandoran2@")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:dorandoran.dev:3478?transport=tcp")
                .setUsername("webrtc")
                .setPassword("dorandoran2@")
                .createIceServer()
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var webSocket: WebSocket? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var audioDeviceModule: AudioDeviceModule? = null

    private val _connectionState = MutableStateFlow(WebRtcConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WebRtcConnectionState> = _connectionState.asStateFlow()

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    private val _remoteAudioTrack = MutableStateFlow<org.webrtc.AudioTrack?>(null)
    val remoteAudioTrack: StateFlow<org.webrtc.AudioTrack?> = _remoteAudioTrack.asStateFlow()
    /**
     * 상대방 오디오 STT 연동 시작 (ViewModel 주입 필요)
     */
    fun startRemoteStt(context: Context, viewModel: org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel) {
        remoteSttHpPrevIn = 0f
        remoteSttHpPrevOut = 0f
        remoteSttAgcGain = 1f
        remoteSttManager = org.duckdns.dorandoran.callaiassistant.stt.SherpaOnnxSttManager(
            context = context,
            onResult = { text ->
                viewModel.updateRemoteSttMessage(text)
            },
            onError = { err -> Log.e(TAG, "Remote STT error: $err") }
        )
        val modelDir = java.io.File(context.filesDir, "sherpa-onnx/sherpa-onnx-streaming-zipformer-korean-2024-06-16")
        remoteSttManager?.initialize(modelDir)
        remoteSttStream = remoteSttManager?.createStream()
        remoteAudioSink = createRemoteAudioSink()
        attachRemoteAudioSink(_remoteAudioTrack.value)
    }

    fun stopRemoteStt() {
        detachRemoteAudioSink()
        remoteAudioSink = null
        remoteSttStream = null
        remoteSttManager = null
        remoteSttHpPrevIn = 0f
        remoteSttHpPrevOut = 0f
        remoteSttAgcGain = 1f
    }

    private fun createRemoteAudioSink(): AudioSink {
        return AudioSink { audioData, bitsPerSample, sampleRate, numberOfChannels, _ ->
            val stream = remoteSttStream ?: return@AudioSink
            if (bitsPerSample != 16 || numberOfChannels <= 0) return@AudioSink

            val shortBuf = java.nio.ByteBuffer
                .wrap(audioData)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
            val input = ShortArray(shortBuf.remaining())
            shortBuf.get(input)

            val mono = if (numberOfChannels == 1) {
                input
            } else {
                // Remote track can arrive as stereo. Downmix to mono for STT.
                val frameCount = input.size / numberOfChannels
                val mixed = ShortArray(frameCount)
                var src = 0
                for (i in 0 until frameCount) {
                    var sum = 0
                    for (ch in 0 until numberOfChannels) {
                        sum += input[src + ch].toInt()
                    }
                    mixed[i] = (sum / numberOfChannels).toShort()
                    src += numberOfChannels
                }
                mixed
            }

            val pcm = FloatArray(mono.size) { i ->
                mono[i].toFloat() / Short.MAX_VALUE
            }
            // sherpa-onnx model is configured for 16 kHz input.
            val sttSampleRate = 16000
            val sttPcm = if (sampleRate != sttSampleRate) {
                resampleFloatPcm(pcm, sampleRate, sttSampleRate)
            } else {
                pcm
            }
            val preprocessed = preprocessRemoteAudioForStt(sttPcm)
            if (preprocessed.isEmpty()) return@AudioSink
            stream.acceptWaveform(preprocessed, sttSampleRate)
            remoteSttManager?.processStream(stream)
        }
    }

    private fun preprocessRemoteAudioForStt(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input

        // Remove low-frequency rumble/DC (helps call-start "booming" artifacts).
        val hpAlpha = 0.973f
        val filtered = FloatArray(input.size)
        var prevIn = remoteSttHpPrevIn
        var prevOut = remoteSttHpPrevOut
        var energy = 0f
        var peak = 0f
        for (i in input.indices) {
            val x = input[i]
            val y = hpAlpha * (prevOut + x - prevIn)
            filtered[i] = y
            prevIn = x
            prevOut = y
            energy += y * y
            val absY = kotlin.math.abs(y)
            if (absY > peak) peak = absY
        }
        remoteSttHpPrevIn = prevIn
        remoteSttHpPrevOut = prevOut

        val rms = kotlin.math.sqrt((energy / filtered.size).coerceAtLeast(1e-9f))
        val targetRms = when {
            rms < 0.010f -> 0.19f
            rms < 0.020f -> 0.16f
            rms < 0.040f -> 0.13f
            else -> 0.10f
        }
        var desiredGain = (targetRms / rms).coerceIn(1f, 18f)
        if (peak > 1e-6f) {
            desiredGain = minOf(desiredGain, 0.97f / peak)
        }
        // Faster attack improves intelligibility for short/quiet remote speech.
        val smooth = if (desiredGain > remoteSttAgcGain) 0.35f else 0.08f
        remoteSttAgcGain = remoteSttAgcGain + (desiredGain - remoteSttAgcGain) * smooth
        if (peak < 0.010f && rms < 0.005f) {
            remoteSttAgcGain = maxOf(remoteSttAgcGain, 2.2f)
        }
        remoteSttAgcGain = remoteSttAgcGain.coerceIn(1f, 18f)

        for (i in filtered.indices) {
            val boosted = filtered[i] * remoteSttAgcGain
            filtered[i] = boosted.coerceIn(-1f, 1f)
        }
        return filtered
    }

    private fun resampleFloatPcm(input: FloatArray, inputRate: Int, outputRate: Int): FloatArray {
        if (input.isEmpty() || inputRate <= 0 || outputRate <= 0 || inputRate == outputRate) {
            return input
        }
        val ratio = inputRate.toDouble() / outputRate.toDouble()
        val outSize = kotlin.math.max(1, (input.size / ratio).toInt())
        val output = FloatArray(outSize)
        for (i in output.indices) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            if (srcIdx + 1 < input.size) {
                val frac = (srcPos - srcIdx).toFloat()
                output[i] = input[srcIdx] * (1f - frac) + input[srcIdx + 1] * frac
            } else {
                output[i] = input[input.lastIndex]
            }
        }
        return output
    }

    private fun attachRemoteAudioSink(track: AudioTrack?) {
        if (track == null) return
        val sink = remoteAudioSink ?: return
        if (remoteAudioSinkAttachedTrack === track) return
        detachRemoteAudioSink()
        try {
            track.addSink(sink)
            remoteAudioSinkAttachedTrack = track
            log("Remote STT AudioSink attached")
        } catch (e: Throwable) {
            log("Failed to attach remote AudioSink: ${e.message}")
        }
    }

    private fun detachRemoteAudioSink() {
        val attachedTrack = remoteAudioSinkAttachedTrack ?: return
        val sink = remoteAudioSink ?: return
        try {
            attachedTrack.removeSink(sink)
            log("Remote STT AudioSink detached")
        } catch (e: Throwable) {
            log("Failed to detach remote AudioSink: ${e.message}")
        } finally {
            remoteAudioSinkAttachedTrack = null
        }
    }

    /** 원격 연결 종료 시 호출 (양쪽 HangUp 처리) */
    var onRemoteDisconnected: (() -> Unit)? = null

    /** 수신 거절됨 (호출자) */
    var onCallRejected: (() -> Unit)? = null

    /** 통화 종료 시 호출 (알림 취소 등) */
    var onCallEnded: (() -> Unit)? = null

    private var receivedOffer = false
    private var offerSent = false
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var hasEverConnected = false
    private var currentCallId: String? = null
    private var lastKnownCallId: String? = null
    private var hangupSent: Boolean = false
    private var callEndedInvoked: Boolean = false
    private var useListeningSocketForSignaling: Boolean = true
    private var iceFailureJob: Job? = null

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        // Custom AudioDeviceModule: 에코 캔슬러 활성화 + TTS PCM 믹싱
        audioDeviceModule = CustomAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        scope.launch {
            _logMessages.value = _logMessages.value + msg
        }
    }

    private fun scheduleIceFailureCheck(reason: String) {
        if (!hasEverConnected) {
            return
        }
        if (iceFailureJob?.isActive == true) {
            return
        }
        iceFailureJob = scope.launch {
            log("ICE failure grace period started ($reason)")
            delay(8000)
            log("ICE failure grace period ended ($reason)")
            onRemoteDisconnected?.invoke()
        }
    }

    /**
     * 호출자: room 접속 후 offer 전송 (callee_joined 수신 시)
     */
    fun joinAsCaller(callId: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                hasEverConnected = false
                hangupSent = false
                callEndedInvoked = false
                useListeningSocketForSignaling = false
                // listening 소켓에서 callee_joined를 받을 수 있도록 연결
                signalingManager?.onSignalingMessage = { text ->
                    scope.launch(Dispatchers.Main.immediate) {
                        handleSignalingMessage(text)
                    }
                }
                doJoin(role = "caller", callId = callId.ifBlank { null })
            }
        }
    }

    /**
     * 수신자: 통화 받기 후 PeerConnection 설정 (listening 소켓 사용)
     */
    fun joinAsCallee(callId: String) {
        hangup(sendSignal = false)
        hasEverConnected = false
        hangupSent = false
        callEndedInvoked = false
        useListeningSocketForSignaling = true
        updateCallId(callId)  // hangup 후에 설정하여 초기화 방지
        receivedOffer = false
        offerSent = false
        pendingIceCandidates.clear()
        log("Setting up as callee (using listening socket)... callId=$callId")
        
        // listening 소켓을 통해 시그널링 메시지를 받도록 설정
        signalingManager?.onSignalingMessage = { text ->
            scope.launch(Dispatchers.Main.immediate) {
                handleSignalingMessage(text)
            }
        }
        
        _connectionState.value = WebRtcConnectionState.CONNECTED
        setupPeerConnection()
        log("PeerConnection ready, waiting for offer...")
    }

    private fun doJoin(role: String, callId: String? = null) {
        hangup(sendSignal = false)
        hasEverConnected = false
        hangupSent = false
        receivedOffer = false
        offerSent = false
        pendingIceCandidates.clear()
        updateCallId(callId)
        log("WS connecting ($role)...")

        val request = Request.Builder()
            .url(SIGNALING_URL)
            .build()

        val client = OkHttpClient.Builder()
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                log("WS connected")
                _connectionState.value = WebRtcConnectionState.CONNECTED
                setupPeerConnection()
                // join + call/accept 순서로 전송 (서버가 callId로 매칭)
                    val joinMsg = JSONObject().apply {
                        put("type", "join")
                        put("roomId", WEBRTC_ROOM_ID)
                        if (callId != null) put("callId", callId)
                        put("role", role)
                    }
                log("WS -> join: ${joinMsg.toString()}")
                webSocket.send(joinMsg.toString())
                log("Join sent")
                when (role) {
                    "caller" -> {
                        log("Join sent (caller), waiting for callee_joined...")
                        scheduleCallIfFirst()
                    }
                    "callee" -> {
                        // Do NOT send 'accept' from the join socket. The server requires
                        // 'accept' to be sent from the existing listening (subscribe) socket.
                        log("Joined as callee (join sent). Waiting for offer...")
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch(Dispatchers.Main.immediate) {
                    handleSignalingMessage(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log("WS error: ${t.message}")
                _connectionState.value = WebRtcConnectionState.DISCONNECTED
                scope.launch { onRemoteDisconnected?.invoke() }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                log("WS closing")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log("WS closed")
                _connectionState.value = WebRtcConnectionState.DISCONNECTED
                scope.launch { onRemoteDisconnected?.invoke() }
            }
        })
    }

    /** 호출자: callee_joined 미수신 시 1.5초 후 offer 전송 (서버 호환용) */
    private fun scheduleCallIfFirst() {
        scope.launch {
            delay(1500)
            if (!offerSent && !receivedOffer && _connectionState.value == WebRtcConnectionState.CONNECTED && peerConnection != null) {
                if (currentCallId == null) {
                    log("Skipping scheduled call: no callId available yet")
                } else {
                    call()
                }
            }
        }
    }

    private fun setupPeerConnection() {
        val factory = peerConnectionFactory ?: return

        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 2
        }

        val constraints = MediaConstraints()
        audioSource = factory.createAudioSource(constraints)
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)
        localAudioTrack?.setEnabled(true)
        log("Local audio track created: enabled=${localAudioTrack?.enabled()}")

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                if (currentCallId == null) {
                    log("Dropping ICE candidate send: missing callId")
                    return
                }
                val json = JSONObject().apply {
                    put("type", "ice")
                    put("roomId", WEBRTC_ROOM_ID)
                    put("callId", currentCallId)
                    put("candidate", JSONObject().apply {
                        put("candidate", candidate.sdp)
                        put("sdpMid", candidate.sdpMid ?: "")
                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                    })
                }
                val message = json.toString()
                sendSignaling(message)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                log("ICE connection: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        iceFailureJob?.cancel()
                        iceFailureJob = null
                        log("ICE connected!")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        log("ICE failed/closed")
                        scheduleIceFailureCheck("ice:$state")
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track() ?: return
                log("onAddTrack: kind=${track.kind()}, id=${track.id()}")
                if (track.kind() == "audio") {
                    log("Remote audio track received: enabled=${track.enabled()}")
                    (track as? org.webrtc.AudioTrack)?.setEnabled(true)
                    scope.launch {
                        _remoteAudioTrack.value = track as org.webrtc.AudioTrack
                        log("Remote audio track set to state flow")
                        attachRemoteAudioSink(_remoteAudioTrack.value)
                    }
                }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                log("Connection: $state")
                when (state) {
                    PeerConnection.PeerConnectionState.CLOSED,
                    PeerConnection.PeerConnectionState.FAILED,
                    PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        scheduleIceFailureCheck("pc:$state")
                    }
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        hasEverConnected = true
                        iceFailureJob?.cancel()
                        iceFailureJob = null
                        CustomAudioDeviceModule.clearTtsQueue()
                        log("TTS queue cleared at call start")
                        _connectionState.value = WebRtcConnectionState.IN_CALL
                    }
                    else -> {}
                }
            }
            override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) {}
        }) ?: run {
            log("Failed to create PeerConnection")
            return
        }

        peerConnection?.addTrack(localAudioTrack, listOf("stream0"))
        log("Local audio track added to peerConnection")
    }

    private fun handleSignalingMessage(text: String) {
        try {
            val msg = JSONObject(text)
            val type = msg.optString("type", "")
            val callIdInfo = if (type == "offer" || type == "answer" || type == "ice") " callId=${msg.optString("callId", "-")}" else ""
            log("WS <- $type$callIdInfo")

            when (type) {
                "callee_joined", "joined" -> {
                    val cid = msg.optString("callId", "")
                    val callIdToUse = if (cid.isNotEmpty()) cid else (currentCallId ?: lastKnownCallId)
                    if (!callIdToUse.isNullOrBlank()) {
                        updateCallId(callIdToUse)
                        log("Callee joined, sending offer...")
                        if (!offerSent) {
                            offerSent = true
                            call()
                        }
                    } else {
                        log("Joined without callId; waiting for callId to send offer")
                    }
                }
                "rejected" -> {
                    log("Call rejected")
                    scope.launch { onCallRejected?.invoke() }
                }
                "offer" -> {
                    receivedOffer = true
                    val offerCallId = msg.optString("callId", "")
                    if (offerCallId.isNotEmpty()) {
                        updateCallId(offerCallId)
                    }
                    val offerSdp = msg.optJSONObject("offer")
                    if (offerSdp != null) {
                        val sdp = offerSdp.optString("sdp")
                        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
                        peerConnection?.setRemoteDescription(object : org.webrtc.SdpObserver {
                            override fun onCreateSuccess(sessionDescription: SessionDescription?) {}
                            override fun onCreateFailure(error: String?) { log("setRemoteDesc fail: $error") }
                            override fun onSetSuccess() {
                                drainPendingIceCandidates()
                                peerConnection?.createAnswer(object : org.webrtc.SdpObserver {
                                    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                                        sessionDescription ?: return
                                        if (currentCallId == null) {
                                            log("Refusing to send answer: missing callId")
                                            return
                                        }
                                        peerConnection?.setLocalDescription(object : org.webrtc.SdpObserver {
                                            override fun onCreateSuccess(p0: SessionDescription?) {}
                                            override fun onCreateFailure(error: String?) {}
                                            override fun onSetSuccess() {
                                                val answer = JSONObject().apply {
                                                    put("type", "answer")
                                                    put("roomId", WEBRTC_ROOM_ID)
                                                    put("callId", currentCallId)
                                                    put("answer", JSONObject().apply {
                                                        put("type", sessionDescription.type.canonicalForm())
                                                        put("sdp", sessionDescription.description)
                                                    })
                                                }
                                                val answerMsg = answer.toString()
                                                sendSignaling(answerMsg)
                                            }
                                            override fun onSetFailure(error: String?) {}
                                        }, sessionDescription)
                                    }
                                    override fun onCreateFailure(error: String?) {}
                                    override fun onSetSuccess() {}
                                    override fun onSetFailure(error: String?) {}
                                }, MediaConstraints())
                            }
                            override fun onSetFailure(error: String?) {}
                        }, offer)
                    }
                }
                "answer" -> {
                    val answerCallId = msg.optString("callId", "")
                    if (answerCallId.isNotEmpty()) {
                        updateCallId(answerCallId)
                    }
                    val answerSdp = msg.optJSONObject("answer")
                    if (answerSdp != null) {
                        val sdp = answerSdp.optString("sdp")
                        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                        peerConnection?.setRemoteDescription(object : org.webrtc.SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(error: String?) {}
                            override fun onSetSuccess() {
                                log("Answer set")
                                drainPendingIceCandidates()
                            }
                            override fun onSetFailure(error: String?) {}
                        }, answer)
                    }
                }
                "ice" -> {
                    val iceCallId = msg.optString("callId", "")
                    if (iceCallId.isNotEmpty()) {
                        updateCallId(iceCallId)
                    }
                    val candidateObj = msg.optJSONObject("candidate")
                    if (candidateObj != null) {
                        val sdp = candidateObj.optString("candidate")
                        val sdpMid = candidateObj.optString("sdpMid")
                        val sdpMLineIndex = candidateObj.optInt("sdpMLineIndex")
                        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                        addIceCandidateOrQueue(iceCandidate)
                    }
                }
                "hangup" -> {
                    val hangupCallId = msg.optString("callId", "")
                    if (!isRelevantCall(hangupCallId)) {
                        log("Ignoring hangup for non-matching callId=$hangupCallId")
                        return
                    }
                    log("Remote hangup received (callId=$hangupCallId)")
                    // 원격에서 hangup을 받으면 로컬 연결 정리 (신호 전송은 하지 않음)
                    scope.launch { onRemoteDisconnected?.invoke() }
                    hangup(sendSignal = false)
                }
                "peer_left" -> {
                    val peerLeftCallId = msg.optString("callId", "")
                    if (!isRelevantCall(peerLeftCallId)) {
                        log("Ignoring peer_left for non-matching callId=$peerLeftCallId")
                        return
                    }
                    log("Remote peer_left received (callId=$peerLeftCallId)")
                    scope.launch { onRemoteDisconnected?.invoke() }
                    hangup(sendSignal = false)
                }
                // 서버가 예기치 않은 타입을 보낼 수 있으므로 로그에 전체 페이로드 출력
                else -> {
                    log("Unhandled signaling type: $type - payload: $text")
                }
            }
        } catch (e: Exception) {
            log("Parse error: ${e.message}")
        }
    }

    private fun addIceCandidateOrQueue(candidate: IceCandidate) {
        val pc = peerConnection ?: return
        if (pc.remoteDescription != null) {
            val ok = pc.addIceCandidate(candidate)
            if (!ok) log("addIceCandidate failed")
        } else {
            pendingIceCandidates.add(candidate)
        }
    }

    private fun drainPendingIceCandidates() {
        val pc = peerConnection ?: return
        pendingIceCandidates.forEach { candidate ->
            val ok = pc.addIceCandidate(candidate)
            if (!ok) log("addIceCandidate failed (drain)")
        }
        pendingIceCandidates.clear()
    }

    fun call() {
        val pc = peerConnection ?: return
        if (currentCallId == null) {
            log("Refusing to create offer: callId is null")
            return
        }
        log("Creating offer...")
        val constraints = MediaConstraints()
        pc.createOffer(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription ?: return
                log("Offer created (${sessionDescription.description.length} bytes)")
                pc.setLocalDescription(object : org.webrtc.SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) { log("setLocalDesc fail: $error") }
                    override fun onSetSuccess() {
                        val offer = JSONObject().apply {
                            put("type", "offer")
                            put("roomId", WEBRTC_ROOM_ID)
                            put("callId", currentCallId)
                            put("offer", JSONObject().apply {
                                put("type", sessionDescription.type.canonicalForm())
                                put("sdp", sessionDescription.description)
                            })
                        }
                        val offerMsg = offer.toString()
                        sendSignaling(offerMsg)
                        offerSent = true
                        log("Offer sent, localDesc set")
                    }
                    override fun onSetFailure(error: String?) {}
                }, sessionDescription)
            }
            override fun onCreateFailure(error: String?) { log("createOffer fail: $error") }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun sendSignaling(message: String) {
        if (useListeningSocketForSignaling && signalingManager != null) {
            signalingManager.sendSignalingMessage(message)
        } else {
            webSocket?.send(message)
        }
    }

    private fun updateCallId(callId: String?) {
        if (!callId.isNullOrBlank()) {
            currentCallId = callId
            lastKnownCallId = callId
        }
    }

    private fun isRelevantCall(callIdFromMsg: String?): Boolean {
        val effectiveCallId = currentCallId ?: lastKnownCallId
        if (effectiveCallId.isNullOrBlank()) {
            return false
        }
        if (callIdFromMsg.isNullOrBlank()) {
            return false
        }
        return callIdFromMsg == effectiveCallId
    }

    fun hangup(sendSignal: Boolean = true) {
        // 이미 hangup이 실행된 경우 중복 실행 방지
        if (peerConnection == null && _connectionState.value == WebRtcConnectionState.DISCONNECTED) {
            log("Hangup already executed, skipping")
            return
        }
        
        val effectiveCallId = currentCallId ?: lastKnownCallId
        if (sendSignal && !hangupSent && effectiveCallId != null) {
            try {
                val json = JSONObject().apply {
                    put("type", "hangup")
                    put("roomId", WEBRTC_ROOM_ID)
                    put("callId", effectiveCallId)
                }
                // 신호 누락을 막기 위해 listening/join 소켓 모두로 전송 시도
                signalingManager?.sendSignalingMessage(json.toString())
                webSocket?.send(json.toString())
                hangupSent = true
                log("WS -> hangup sent")
                // 웹소켓 닫기를 지연시켜 서버로 메시지가 전달될 시간을 확보
                scope.launch {
                    try {
                        kotlinx.coroutines.delay(500)
                        try { webSocket?.close(1000, "hangup") } catch (_: Throwable) {}
                        webSocket = null
                        log("WebSocket closed after delay")
                    } catch (_: Throwable) {}
                }
            } catch (e: Exception) {
                log("Failed to send hangup: ${e.message}")
            }
        } else {
            try { webSocket?.close(1000, "hangup") } catch (_: Throwable) {}
            webSocket = null
        }

        peerConnection?.close()
        peerConnection = null
        detachRemoteAudioSink()
        CustomAudioDeviceModule.clearTtsQueue()
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        _remoteAudioTrack.value = null
        _connectionState.value = WebRtcConnectionState.DISCONNECTED
        currentCallId = null
        lastKnownCallId = null
        hasEverConnected = false
        log("Hangup")
        
        // 통화 종료 콜백 호출 (알림 취소 등) - 한 번만 호출
        if (!callEndedInvoked) {
            callEndedInvoked = true
            onCallEnded?.invoke()
        }
    }

    fun clearLog() {
        _logMessages.value = emptyList()
    }

    fun release() {
        hangup()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        try {
            audioDeviceModule?.release()
        } catch (_: Throwable) {}
        audioDeviceModule = null
    }
}

enum class WebRtcConnectionState {
    DISCONNECTED,
    CONNECTED,
    IN_CALL
}
