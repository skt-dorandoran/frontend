package org.duckdns.dorandoran.callaiassistant.tts

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.util.Log
import org.duckdns.dorandoran.callaiassistant.webrtc.CustomAudioDeviceModule

/**
 * TTS(Text-to-Speech) 엔진 관리
 * - sherpa-onnx + Espeak 온디바이스 TTS 사용
 * - PCM을 WebRTC에 직접 주입하여 상대방에게 전송
 */
object TtsManager {

    private const val TAG = "TtsManager"
    private var isInitialized = false
    private var isShuttingDown = false

    /**
     * TTS 초기화 (sherpa-onnx Espeak 사용)
     */
    fun initializeForCall(
        context: Context,
        onReady: (TextToSpeech?) -> Unit,
        onDone: (() -> Unit)? = null
    ): TextToSpeech? {
        // 통화 시작 시 항상 초기화
        if (!isInitialized || isShuttingDown) {
            isShuttingDown = false
            SherpaOnnxTtsManager.initialize(
                context = context,
                onReady = {
                    isInitialized = true
                    Log.i(TAG, "TTS initialized with sherpa-onnx Espeak")
                    onReady(null)
                },
                onError = { error ->
                    Log.e(TAG, "TTS initialization failed: $error")
                    isInitialized = false
                    onReady(null)
                }
            )
        } else {
            // 이미 초기화되어 있으면 TTS 큐 초기화
            CustomAudioDeviceModule.clearTtsQueue()
            onReady(null)
        }
        return null
    }

    /**
     * 텍스트 재생 - Espeak TTS로 생성하여 AudioTrack 재생 (WebRTC 마이크가 캡처)
     */
    fun speak(tts: TextToSpeech?, text: String, audioManager: AudioManager, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        
        // sherpa-onnx Espeak으로 TTS 재생 (AudioManager 전달, onDone 콜백 전달)
        SherpaOnnxTtsManager.speak(text = text, audioManager = audioManager, onDone = onDone)
    }

    /**
     * TTS 정지 및 리소스 해제
     */
    fun shutdown(tts: TextToSpeech?) {
        // SherpaOnnxTtsManager 즉시 종료 (재생 중단)
        SherpaOnnxTtsManager.shutdown()
        // TTS 큐 초기화
        CustomAudioDeviceModule.clearTtsQueue()
        isShuttingDown = true
        isInitialized = false
        Log.d(TAG, "TTS Manager shutdown complete")
    }
}
