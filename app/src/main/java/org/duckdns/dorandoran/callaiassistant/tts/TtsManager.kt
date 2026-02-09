package org.duckdns.dorandoran.callaiassistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * TTS(Text-to-Speech) 엔진 관리
 * - 기본: Android 내장 TTS 엔진 사용
 * - 추후 엔진 변경 시 [getPreferredEngine] 및 [createTextToSpeech] 수정
 */
object TtsManager {

    private const val TAG = "TtsManager"

    /**
     * 사용할 TTS 엔진 패키지명
     * null = 시스템 기본 엔진 (안드로이드 내장 우선)
     * 추후 변경: "com.google.android.tts" 등 특정 엔진 지정 가능
     */
    fun getPreferredEngine(): String? = null

    /**
     * TTS 인스턴스 생성 (엔진 지정 로직)
     * 추후 커스텀 엔진 사용 시 이 함수만 수정
     */
    fun createTextToSpeech(context: Context, listener: TextToSpeech.OnInitListener): TextToSpeech {
        val engine = getPreferredEngine()
        return if (engine != null) {
            TextToSpeech(context, listener, engine)
        } else {
            TextToSpeech(context, listener)
        }
    }

    /**
     * 통화 중 TTS 재생용 오디오 속성
     * USAGE_VOICE_COMMUNICATION: 통화 오디오 경로 사용 (스피커폰 시 상대방이 들을 수 있음)
     */
    fun getCallAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /**
     * TTS 초기화 및 통화용 설정
     * @param onReady 초기화 완료 시 콜백 (speak 호출 가능)
     * @param onDone 재생 완료 시 콜백 (선택)
     */
    fun initializeForCall(
        context: Context,
        onReady: (TextToSpeech) -> Unit,
        onDone: (() -> Unit)? = null
    ): TextToSpeech {
        var ttsRef: TextToSpeech? = null
        val tts = createTextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsRef?.let { t ->
                    t.language = Locale.KOREAN
                    t.setAudioAttributes(getCallAudioAttributes())
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            onDone?.invoke()
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            Log.e(TAG, "TTS error: $utteranceId")
                            onDone?.invoke()
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.e(TAG, "TTS error: $errorCode")
                            onDone?.invoke()
                        }
                    })
                    onReady(t)
                }
            } else {
                Log.e(TAG, "TTS init failed: $status")
            }
        }
        ttsRef = tts
        return tts
    }

    /**
     * 텍스트 재생 (통화 중)
     * 스피커폰 모드에서 상대방이 들을 수 있도록 스피커 활성화 권장
     */
    fun speak(tts: TextToSpeech, text: String, audioManager: AudioManager) {
        if (text.isBlank()) return
        tts.stop()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "call_tts")
        }
        // 통화 중 스피커폰으로 TTS 출력 → 마이크가 포착하여 상대방에게 전달
        audioManager.isSpeakerphoneOn = true
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "call_tts")
    }

    /**
     * TTS 정지 및 리소스 해제
     */
    fun shutdown(tts: TextToSpeech?) {
        tts?.stop()
        tts?.shutdown()
    }
}
