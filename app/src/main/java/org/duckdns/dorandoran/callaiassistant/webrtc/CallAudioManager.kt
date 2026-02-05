package org.duckdns.dorandoran.callaiassistant.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * WebRTC 통화용 오디오 관리 (AppRTCAudioManager 패턴 기반)
 * - MODE_IN_COMMUNICATION 설정으로 VoIP 최적화
 * - STREAM_VOICE_CALL 오디오 포커스 선점
 * - 통화연결음과 WebRTC가 동일한 오디오 경로를 사용하도록 선제 설정
 */
class CallAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "CallAudioManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedAudioMode = AudioManager.MODE_NORMAL
    private var savedSpeakerphoneOn = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    /**
     * 통화 시작 시 호출. WebRTC join() 전에 호출하여 오디오 경로를 선점.
     */
    fun start() {
        try {
            savedAudioMode = audioManager.mode
            savedSpeakerphoneOn = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
            requestAudioFocus()
            Log.d(TAG, "CallAudioManager started, mode=MODE_IN_COMMUNICATION, speakerphone=off")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CallAudioManager", e)
        }
    }

    /**
     * 통화 종료 시 호출. 원래 오디오 모드 복원.
     */
    fun stop() {
        try {
            abandonAudioFocus()
            audioManager.isSpeakerphoneOn = savedSpeakerphoneOn
            audioManager.mode = savedAudioMode
            Log.d(TAG, "CallAudioManager stopped, mode restored to $savedAudioMode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop CallAudioManager", e)
        }
    }

    /**
     * 스피커폰 모드 토글
     */
    fun setSpeakerphone(on: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = on
            Log.d(TAG, "Speakerphone set to $on")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set speakerphone", e)
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            // Ensure we supply a listener - required when using delayed focus or ducking behavior
            if (audioFocusListener == null) {
                audioFocusListener = AudioManager.OnAudioFocusChangeListener { /* no-op */ }
            }
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusListener!!)
                .build()
            audioFocusRequest = request
            val result = audioManager.requestAudioFocus(request)
            Log.d(TAG, "Audio focus request result: $result")
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
            Log.d(TAG, "Audio focus request result (legacy): $result")
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
