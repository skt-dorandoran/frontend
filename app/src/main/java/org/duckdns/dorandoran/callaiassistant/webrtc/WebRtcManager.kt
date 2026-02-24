package org.duckdns.dorandoran.callaiassistant.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
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
import kotlin.math.abs
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
import java.util.UUID

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
    // 상대방/내 오디오 STT 상태는 WebRtcManager 인스턴스별로 분리한다.
    private var remoteSttWsClient: org.duckdns.dorandoran.callaiassistant.stt.RealtimeTranscribeWsClient? = null
    private var remoteAudioSink: AudioSink? = null
    private var remoteAudioSinkAttachedTrack: AudioTrack? = null
    private var remoteSttHpPrevIn: Float = 0f
    private var remoteSttHpPrevOut: Float = 0f
    private var remoteSttAgcGain: Float = 1f
    private var remoteSttStartJob: Job? = null
    private var localSttWsClient: org.duckdns.dorandoran.callaiassistant.stt.RealtimeTranscribeWsClient? = null
    private var localSttHpPrevIn: Float = 0f
    private var localSttHpPrevOut: Float = 0f
    private var localSttAgcGain: Float = 1f
    private var localSttStartJob: Job? = null
    @Volatile
    private var remoteSttRecentRms: Float = 0f
    @Volatile
    private var remoteSpeechHoldUntilMs: Long = 0L
    @Volatile
    private var localSilenceDetectedAtMs: Long = 0L
    @Volatile
    private var remoteSilenceDetectedAtMs: Long = 0L
    @Volatile
    private var localLastSilenceDurationSec: Double = 0.0
    @Volatile
    private var remoteLastSilenceDurationSec: Double = 0.0
    @Volatile
    private var interventionSuppressedUntilMs: Long = 0L
    @Volatile
    private var interventionPlaybackActive: Boolean = false
    @Volatile
    private var lastInterventionTriggeredAtMs: Long = 0L

    private val _isRemoteSpeaking = MutableStateFlow(false)
    val isRemoteSpeaking: StateFlow<Boolean> = _isRemoteSpeaking.asStateFlow()

    // 로컬/원격 WS의 이벤트 타이밍 차이를 흡수하기 위해 여유를 넓힌다.
    private val dualSilenceSyncWindowMs = 4500L
    private val interventionMinIntervalMs = 2500L
    private val interventionCooldownAfterPlaybackMs = 3200L
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    @Volatile
    private var speakerphoneRouteHint: Boolean = false
    @Volatile
    private var bluetoothRouteActive: Boolean = false
    @Volatile
    private var speakerRouteActive: Boolean = false
    @Volatile
    private var lastRouteRefreshMs: Long = 0L
    @Volatile
    private var localEchoGuardGain: Float = 1f
    @Volatile
    private var localEchoGuardActive: Boolean = false
    @Volatile
    private var localEchoGuardHoldUntilMs: Long = 0L
    @Volatile
    private var localTxUserEnabled: Boolean = true
    @Volatile
    private var localTxEchoSuppressed: Boolean = false
    @Volatile
    private var localTxEchoSuppressHoldUntilMs: Long = 0L
    @Volatile
    private var textCallModeActive: Boolean = false

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
    private val _remoteAudioLevel = MutableStateFlow(0f)
    val remoteAudioLevel: StateFlow<Float> = _remoteAudioLevel.asStateFlow()
    @Volatile
    private var remoteAudioLevelSmoothed = 0f
    @Volatile
    private var remoteAudioLevelLastEmitMs = 0L
    /**
     * 상대방 오디오 STT 연동 시작 (ViewModel 주입 필요)
     */
    fun startRemoteStt(context: Context, viewModel: org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel) {
        if (remoteSttStartJob?.isActive == true || remoteSttWsClient != null) return
        remoteSttHpPrevIn = 0f
        remoteSttHpPrevOut = 0f
        remoteSttAgcGain = 1f
        remoteSttStartJob = scope.launch(Dispatchers.Default) {
            val sttClient = org.duckdns.dorandoran.callaiassistant.stt.RealtimeTranscribeWsClient(
                tag = "$TAG-RemoteStt",
                silenceThresholdSeconds = 3.0,
                callId = buildSttCallId("remote"),
                onInterim = { payload ->
                    if (payload.text.isNotBlank()) {
                        viewModel.updateRemoteSttMessage(payload, isFinal = false)
                    }
                },
                onFinal = { payload ->
                    if (payload.text.isNotBlank()) {
                        viewModel.updateRemoteSttMessage(payload, isFinal = true)
                    }
                },
                onSilenceDetected = { duration ->
                    onRemoteSilenceDetected(duration, viewModel)
                },
                onError = { err -> Log.e(TAG, "Remote STT error: $err") }
            )
            sttClient.connect()
            withContext(Dispatchers.Main.immediate) {
                remoteSttWsClient = sttClient
                remoteAudioSink = createRemoteAudioSink()
                attachRemoteAudioSink(_remoteAudioTrack.value)
                log("Remote STT initialized (server ws)")
            }
        }
    }

    fun stopRemoteStt() {
        remoteSttStartJob?.cancel()
        remoteSttStartJob = null
        detachRemoteAudioSink()
        remoteAudioSink = null
        remoteSttWsClient?.disconnect()
        remoteSttWsClient = null
        remoteSttHpPrevIn = 0f
        remoteSttHpPrevOut = 0f
        remoteSttAgcGain = 1f
        remoteAudioLevelSmoothed = 0f
        remoteAudioLevelLastEmitMs = 0L
        remoteSttRecentRms = 0f
        _remoteAudioLevel.value = 0f
        remoteSpeechHoldUntilMs = 0L
        _isRemoteSpeaking.value = false
        remoteSilenceDetectedAtMs = 0L
        remoteLastSilenceDurationSec = 0.0
    }

    fun startLocalStt(context: Context, viewModel: org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel) {
        if (localSttStartJob?.isActive == true || localSttWsClient != null) return
        localSttHpPrevIn = 0f
        localSttHpPrevOut = 0f
        localSttAgcGain = 1f
        localSttStartJob = scope.launch(Dispatchers.Default) {
            val sttClient = org.duckdns.dorandoran.callaiassistant.stt.RealtimeTranscribeWsClient(
                tag = "$TAG-LocalStt",
                silenceThresholdSeconds = 3.0,
                callId = buildSttCallId("local"),
                onInterim = { payload ->
                    if (payload.text.isNotBlank()) {
                        viewModel.updateMySttMessage(payload, isFinal = false)
                    }
                },
                onFinal = { payload ->
                    if (payload.text.isNotBlank()) {
                        viewModel.updateMySttMessage(payload, isFinal = true)
                    }
                },
                onSilenceDetected = { duration ->
                    onLocalSilenceDetected(duration, viewModel)
                },
                onComprehension = { payload ->
                    if (payload.status.equals("alert", ignoreCase = true) || payload.enableAiCorrection) {
                        viewModel.onComprehensionAlert()
                        triggerInterventionIfAllowed(
                            source = "comprehension",
                            fallbackSilenceDurationSec = 0.0,
                            viewModel = viewModel
                        )
                    }
                },
                onError = { err -> Log.e(TAG, "Local STT error: $err") }
            )
            sttClient.connect()
            withContext(Dispatchers.Main.immediate) {
                localSttWsClient = sttClient
                CustomAudioDeviceModule.setMicSamplesListener { audioData, sampleRate, numberOfChannels, bitsPerSample ->
                    val client = localSttWsClient ?: return@setMicSamplesListener
                    if (bitsPerSample != 16 || numberOfChannels <= 0) return@setMicSamplesListener
                    val pcm = pcm16BytesToMonoFloat(audioData, numberOfChannels)
                    if (pcm.isEmpty()) return@setMicSamplesListener
                    val sttSampleRate = 16000
                    val sttPcm = if (sampleRate != sttSampleRate) {
                        resampleFloatPcm(pcm, sampleRate, sttSampleRate)
                    } else {
                        pcm
                    }
                    val preprocessed = preprocessLocalAudioForStt(sttPcm)
                    if (preprocessed.isEmpty()) return@setMicSamplesListener
                    client.sendPcm16Mono16k(floatToPcm16Bytes(preprocessed))
                }
                log("Local STT initialized (server ws)")
            }
        }
    }

    fun stopLocalStt() {
        localSttStartJob?.cancel()
        localSttStartJob = null
        CustomAudioDeviceModule.setMicSamplesListener(null)
        localSttWsClient?.disconnect()
        localSttWsClient = null
        localSttHpPrevIn = 0f
        localSttHpPrevOut = 0f
        localSttAgcGain = 1f
        localEchoGuardGain = 1f
        localEchoGuardActive = false
        localEchoGuardHoldUntilMs = 0L
        localTxEchoSuppressed = false
        localTxEchoSuppressHoldUntilMs = 0L
        applyLocalAudioTrackEnabled()
        localSilenceDetectedAtMs = 0L
        localLastSilenceDurationSec = 0.0
        interventionSuppressedUntilMs = 0L
        interventionPlaybackActive = false
        lastInterventionTriggeredAtMs = 0L
    }

    private fun createRemoteAudioSink(): AudioSink {
        return AudioSink { audioData, bitsPerSample, sampleRate, numberOfChannels, _ ->
            val client = remoteSttWsClient ?: return@AudioSink
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
            val sttSampleRate = 16000
            val sttPcm = if (sampleRate != sttSampleRate) {
                resampleFloatPcm(pcm, sampleRate, sttSampleRate)
            } else {
                pcm
            }
            updateRemoteAudioLevel(sttPcm)
            val preprocessed = preprocessRemoteAudioForStt(sttPcm)
            if (preprocessed.isEmpty()) return@AudioSink
            client.sendPcm16Mono16k(floatToPcm16Bytes(preprocessed))
        }
    }

    private fun updateRemoteAudioLevel(pcm: FloatArray) {
        if (pcm.isEmpty()) return
        var energy = 0f
        var peak = 0f
        for (sample in pcm) {
            val absSample = kotlin.math.abs(sample)
            energy += sample * sample
            if (absSample > peak) peak = absSample
        }
        val rms = kotlin.math.sqrt((energy / pcm.size).coerceAtLeast(1e-9f))
        val normalized = (rms * 3.2f + peak * 0.22f).coerceIn(0f, 1f)
        val previous = remoteAudioLevelSmoothed
        val smoothed = if (normalized > previous) {
            previous + (normalized - previous) * 0.45f
        } else {
            previous + (normalized - previous) * 0.12f
        }.let { if (it < 0.015f) 0f else it }

        val nowMs = System.currentTimeMillis()
        if (normalized >= 0.055f) {
            remoteSpeechHoldUntilMs = nowMs + 800L
        }
        refreshRemoteSpeakingState(nowMs)
        if (nowMs - remoteAudioLevelLastEmitMs >= 33L || abs(smoothed - _remoteAudioLevel.value) >= 0.018f) {
            remoteAudioLevelSmoothed = smoothed
            remoteAudioLevelLastEmitMs = nowMs
            _remoteAudioLevel.value = smoothed
        } else {
            remoteAudioLevelSmoothed = smoothed
        }
    }

    private fun refreshRemoteSpeakingState(nowMs: Long = System.currentTimeMillis()) {
        val speaking = nowMs < remoteSpeechHoldUntilMs
        if (_isRemoteSpeaking.value != speaking) {
            _isRemoteSpeaking.value = speaking
        }
    }

    private fun onLocalSilenceDetected(
        durationSec: Double,
        viewModel: org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel
    ) {
        localSilenceDetectedAtMs = System.currentTimeMillis()
        localLastSilenceDurationSec = durationSec
        maybeTriggerDualSilenceIntervention(viewModel)
    }

    private fun onRemoteSilenceDetected(
        durationSec: Double,
        viewModel: org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel
    ) {
        remoteSilenceDetectedAtMs = System.currentTimeMillis()
        remoteLastSilenceDurationSec = durationSec
        // 서버가 원격 무음을 확정했으면 휴리스틱 발화 상태는 즉시 해제한다.
        remoteSpeechHoldUntilMs = 0L
        refreshRemoteSpeakingState()
        maybeTriggerDualSilenceIntervention(viewModel)
    }

    private fun maybeTriggerDualSilenceIntervention(
        viewModel: org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel
    ) {
        val now = System.currentTimeMillis()
        if (localSilenceDetectedAtMs <= 0L || remoteSilenceDetectedAtMs <= 0L) return
        val delta = kotlin.math.abs(localSilenceDetectedAtMs - remoteSilenceDetectedAtMs)
        val bothRecent = now - localSilenceDetectedAtMs <= dualSilenceSyncWindowMs &&
            now - remoteSilenceDetectedAtMs <= dualSilenceSyncWindowMs
        if (!bothRecent || delta > dualSilenceSyncWindowMs) return
        val mergedDuration = maxOf(localLastSilenceDurationSec, remoteLastSilenceDurationSec)
        triggerInterventionIfAllowed(
            source = "dual_silence",
            fallbackSilenceDurationSec = mergedDuration,
            viewModel = viewModel
        )
    }

    private fun triggerInterventionIfAllowed(
        source: String,
        fallbackSilenceDurationSec: Double,
        viewModel: org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel
    ) {
        val now = System.currentTimeMillis()
        refreshRemoteSpeakingState(now)

        val isWithinSuppression = now < interventionSuppressedUntilMs
        val intervalBlocked = now - lastInterventionTriggeredAtMs < interventionMinIntervalMs
        val remoteSpeaking = _isRemoteSpeaking.value
        val shouldBlockByRemoteSpeaking = source != "dual_silence" && remoteSpeaking
        if (interventionPlaybackActive || isWithinSuppression || intervalBlocked || shouldBlockByRemoteSpeaking) {
            log(
                "Intervention blocked source=$source " +
                "playbackActive=$interventionPlaybackActive suppressed=$isWithinSuppression " +
                    "intervalBlocked=$intervalBlocked remoteSpeaking=$remoteSpeaking"
            )
            return
        }

        lastInterventionTriggeredAtMs = now
        viewModel.onSilenceDetected(fallbackSilenceDurationSec)
    }

    fun markInterventionPlaybackStarted() {
        interventionPlaybackActive = true
        interventionSuppressedUntilMs = System.currentTimeMillis() + interventionCooldownAfterPlaybackMs
    }

    fun markInterventionPlaybackFinished() {
        interventionPlaybackActive = false
        interventionSuppressedUntilMs = System.currentTimeMillis() + interventionCooldownAfterPlaybackMs
        // 개입 TTS 직후에는 무음 카운트를 새로 시작하도록 최근 이벤트를 비운다.
        localSilenceDetectedAtMs = 0L
        remoteSilenceDetectedAtMs = 0L
    }

    private fun buildSttCallId(suffix: String): String {
        val base = (currentCallId ?: lastKnownCallId)?.trim().orEmpty()
        return if (base.isNotBlank()) {
            "${base}_$suffix"
        } else {
            "call_${UUID.randomUUID().toString().replace("-", "").take(12)}_$suffix"
        }
    }

    fun setLocalAudioTransmissionEnabled(enabled: Boolean) {
        localTxUserEnabled = enabled
        applyLocalAudioTrackEnabled()
        log("Local audio transmission user=${if (enabled) "enabled" else "muted"}")
    }

    private fun applyLocalAudioTrackEnabled() {
        val shouldEnable = localTxUserEnabled && !localTxEchoSuppressed
        localAudioTrack?.setEnabled(shouldEnable)
    }

    fun setSpeakerphoneHint(enabled: Boolean) {
        speakerphoneRouteHint = enabled
        refreshAudioRouteState(force = true)
    }

    fun setTextCallModeActive(enabled: Boolean) {
        textCallModeActive = enabled
        log("Text call STT isolation ${if (enabled) "enabled" else "disabled"}")
    }

    private fun refreshAudioRouteState(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRouteRefreshMs < 250L) return
        lastRouteRefreshMs = now

        var routeBluetooth = false
        var routeSpeaker = speakerphoneRouteHint

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val communicationDevice = audioManager.communicationDevice
            routeBluetooth = communicationDevice?.let { it.isBluetoothCommunicationDevice() } ?: false
            routeSpeaker = routeSpeaker || communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
            @Suppress("DEPRECATION")
            routeSpeaker = routeSpeaker || audioManager.isSpeakerphoneOn
        }

        @Suppress("DEPRECATION")
        routeSpeaker = routeSpeaker || audioManager.isSpeakerphoneOn
        bluetoothRouteActive = routeBluetooth
        speakerRouteActive = routeSpeaker
    }

    private fun AudioDeviceInfo.isBluetoothCommunicationDevice(): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            type == AudioDeviceInfo.TYPE_HEARING_AID
    }

    private fun preprocessRemoteAudioForStt(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input

        // Remove low-frequency rumble/DC (helps call-start "booming" artifacts).
        val hpAlpha = 0.94f
        val filtered = FloatArray(input.size)
        var prevIn = remoteSttHpPrevIn
        var prevOut = remoteSttHpPrevOut
        var energy = 0f
        var peak = 0f
        for (i in input.indices) {
            val x = input[i]
            val yHp = hpAlpha * (prevOut + x - prevIn)
            // Keep a portion of original signal to preserve muffled articulation.
            val y = (yHp * 0.75f) + (x * 0.25f)
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
        remoteSttRecentRms = (remoteSttRecentRms * 0.90f) + (rms * 0.10f)
        val targetRms = when {
            rms < 0.012f -> 0.16f
            rms < 0.025f -> 0.13f
            rms < 0.05f -> 0.11f
            else -> 0.10f
        }
        var desiredGain = (targetRms / rms).coerceIn(1f, 16f)
        if (peak > 1e-6f) {
            desiredGain = minOf(desiredGain, 0.97f / peak)
        }
        // Moderate AGC to avoid over-amplified artifacts on remote/TTS audio.
        val smooth = if (desiredGain > remoteSttAgcGain) 0.25f else 0.08f
        remoteSttAgcGain = remoteSttAgcGain + (desiredGain - remoteSttAgcGain) * smooth
        if (peak < 0.010f && rms < 0.005f) {
            remoteSttAgcGain = maxOf(remoteSttAgcGain, 1.5f)
        }
        remoteSttAgcGain = remoteSttAgcGain.coerceIn(1f, 16f)

        for (i in filtered.indices) {
            val boosted = filtered[i] * remoteSttAgcGain
            filtered[i] = boosted.coerceIn(-1f, 1f)
        }
        return filtered
    }

    private fun preprocessLocalAudioForStt(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input
        refreshAudioRouteState()
        val hpAlpha = 0.94f
        val filtered = FloatArray(input.size)
        var prevIn = localSttHpPrevIn
        var prevOut = localSttHpPrevOut
        var energy = 0f
        var peak = 0f
        for (i in input.indices) {
            val x = input[i]
            val yHp = hpAlpha * (prevOut + x - prevIn)
            val y = (yHp * 0.72f) + (x * 0.28f)
            filtered[i] = y
            prevIn = x
            prevOut = y
            energy += y * y
            val absY = kotlin.math.abs(y)
            if (absY > peak) peak = absY
        }
        localSttHpPrevIn = prevIn
        localSttHpPrevOut = prevOut

        val now = System.currentTimeMillis()
        val rms = kotlin.math.sqrt((energy / filtered.size).coerceAtLeast(1e-9f))
        val remoteRms = remoteSttRecentRms
        val localDominance = if (remoteRms > 1e-6f) rms / remoteRms else 1f
        val echoGuardEnabled = speakerRouteActive && !bluetoothRouteActive
        var hardBlockEchoFrame = false
        if (echoGuardEnabled) {
            val echoLikely = remoteRms > 0.050f && localDominance < 0.45f && peak < 0.30f
            if (echoLikely) {
                localEchoGuardActive = true
                localEchoGuardHoldUntilMs = now + 220L
                val remoteSpeakingNow = now < remoteSpeechHoldUntilMs
                // 텍스트 통화 모드에서는 원격 음성이 로컬 STT(오른쪽 버블)로 섞이지 않도록
                // 에코 의심 프레임을 강하게 차단한다.
                if (textCallModeActive && remoteSpeakingNow) {
                    hardBlockEchoFrame = true
                }
                localTxEchoSuppressHoldUntilMs = now + 280L
                if (!localTxEchoSuppressed) {
                    localTxEchoSuppressed = true
                    applyLocalAudioTrackEnabled()
                    log("Local uplink temporarily suppressed (echo-loop guard)")
                }
            } else if (now > localEchoGuardHoldUntilMs) {
                localEchoGuardActive = false
            }
        } else {
            localEchoGuardActive = false
            localEchoGuardHoldUntilMs = 0L
        }

        if (localTxEchoSuppressed && now > localTxEchoSuppressHoldUntilMs) {
            localTxEchoSuppressed = false
            applyLocalAudioTrackEnabled()
            log("Local uplink restored (echo-loop guard)")
        }

        if (hardBlockEchoFrame) {
            return FloatArray(0)
        }

        val echoGuardTarget = if (localEchoGuardActive) 0.22f else 1.0f
        val echoGuardSmoothing = if (echoGuardTarget < localEchoGuardGain) 0.35f else 0.08f
        localEchoGuardGain += (echoGuardTarget - localEchoGuardGain) * echoGuardSmoothing
        localEchoGuardGain = localEchoGuardGain.coerceIn(0.18f, 1.0f)

        val targetRms = when {
            rms < 0.010f -> 0.17f
            rms < 0.020f -> 0.14f
            rms < 0.040f -> 0.12f
            else -> 0.10f
        }
        var desiredGain = (targetRms / rms).coerceIn(1f, 14f)
        if (peak > 1e-6f) {
            desiredGain = minOf(desiredGain, 0.97f / peak)
        }
        val smooth = if (desiredGain > localSttAgcGain) 0.22f else 0.08f
        localSttAgcGain = localSttAgcGain + (desiredGain - localSttAgcGain) * smooth
        if (peak < 0.010f && rms < 0.005f) {
            localSttAgcGain = maxOf(localSttAgcGain, 1.5f)
        }
        localSttAgcGain = localSttAgcGain.coerceIn(1f, 14f)
        for (i in filtered.indices) {
            filtered[i] = (filtered[i] * localSttAgcGain * localEchoGuardGain).coerceIn(-1f, 1f)
        }
        return filtered
    }

    private fun pcm16BytesToMonoFloat(audioData: ByteArray, numberOfChannels: Int): FloatArray {
        if (audioData.isEmpty() || numberOfChannels <= 0) return FloatArray(0)
        val shortBuf = java.nio.ByteBuffer
            .wrap(audioData)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        if (!shortBuf.hasRemaining()) return FloatArray(0)

        val input = ShortArray(shortBuf.remaining())
        shortBuf.get(input)
        if (input.isEmpty()) return FloatArray(0)

        val monoShort = if (numberOfChannels == 1) {
            input
        } else {
            val frameCount = input.size / numberOfChannels
            if (frameCount <= 0) return FloatArray(0)
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

        return FloatArray(monoShort.size) { i ->
            monoShort[i].toFloat() / Short.MAX_VALUE
        }
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

    private fun floatToPcm16Bytes(input: FloatArray): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val bytes = ByteArray(input.size * 2)
        var idx = 0
        input.forEach { sample ->
            val clamped = sample.coerceIn(-1f, 1f)
            val pcm = (clamped * Short.MAX_VALUE).toInt().toShort()
            bytes[idx] = (pcm.toInt() and 0xFF).toByte()
            bytes[idx + 1] = ((pcm.toInt() shr 8) and 0xFF).toByte()
            idx += 2
        }
        return bytes
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
            // 하울링/에코 억제를 위해 통화 최적화 캡처 소스를 사용한다.
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
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

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        audioSource = factory.createAudioSource(constraints)
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)
        applyLocalAudioTrackEnabled()
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
        stopRemoteStt()
        stopLocalStt()
        detachRemoteAudioSink()
        CustomAudioDeviceModule.clearTtsQueue()
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        _remoteAudioTrack.value = null
        remoteAudioLevelSmoothed = 0f
        remoteAudioLevelLastEmitMs = 0L
        _remoteAudioLevel.value = 0f
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
