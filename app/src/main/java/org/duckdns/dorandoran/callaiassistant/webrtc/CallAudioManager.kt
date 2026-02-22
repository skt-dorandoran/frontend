package org.duckdns.dorandoran.callaiassistant.webrtc

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
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
    private var isStarted = false
    private var manualSpeakerEnabled = false
    private var audioDeviceCallback: AudioDeviceCallback? = null

    /**
     * 통화 시작 시 호출. WebRTC join() 전에 호출하여 오디오 경로를 선점.
     */
    fun start() {
        if (isStarted) {
            refreshRoute()
            return
        }
        try {
            savedAudioMode = audioManager.mode
            savedSpeakerphoneOn = audioManager.isSpeakerphoneOn
            manualSpeakerEnabled = false
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            requestAudioFocus()
            registerAudioDeviceCallback()
            refreshRoute()
            isStarted = true
            Log.d(TAG, "CallAudioManager started, mode=MODE_IN_COMMUNICATION")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CallAudioManager", e)
        }
    }

    /**
     * 통화 종료 시 호출. 원래 오디오 모드 복원.
     */
    fun stop() {
        try {
            unregisterAudioDeviceCallback()
            abandonAudioFocus()
            clearCommunicationDevice()
            audioManager.isSpeakerphoneOn = savedSpeakerphoneOn
            audioManager.mode = savedAudioMode
            manualSpeakerEnabled = false
            isStarted = false
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
            manualSpeakerEnabled = on
            refreshRoute()
            Log.d(TAG, "Speakerphone preference set to $on")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set speakerphone", e)
        }
    }

    private fun registerAudioDeviceCallback() {
        if (audioDeviceCallback != null) return
        audioDeviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                refreshRoute()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                refreshRoute()
            }
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback!!, null)
    }

    private fun unregisterAudioDeviceCallback() {
        audioDeviceCallback?.let {
            audioManager.unregisterAudioDeviceCallback(it)
            audioDeviceCallback = null
        }
    }

    private fun refreshRoute() {
        if (!isStarted && audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) return

        val communicationDevices = getCommunicationDevices()
        val bluetoothDevice = communicationDevices.firstOrNull { it.isBluetoothCommunicationDevice() }
        val speakerDevice = communicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val earpieceDevice = communicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }

        when {
            bluetoothDevice != null -> {
                setCommunicationDevice(bluetoothDevice, "bluetooth")
            }
            manualSpeakerEnabled && speakerDevice != null -> {
                setCommunicationDevice(speakerDevice, "speaker(manual)")
            }
            speakerDevice != null -> {
                // 요구사항: 이어폰 끊김 시 스마트폰 스피커/마이크로 즉시 복귀
                setCommunicationDevice(speakerDevice, "speaker(auto-fallback)")
            }
            earpieceDevice != null -> {
                setCommunicationDevice(earpieceDevice, "earpiece(fallback)")
            }
            else -> {
                clearCommunicationDevice()
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = !manualSpeakerEnabled
                Log.w(TAG, "No communication devices, fallback to legacy speakerphone state")
            }
        }
    }

    private fun getCommunicationDevices(): List<AudioDeviceInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
        } else {
            emptyList()
        }
    }

    private fun setCommunicationDevice(device: AudioDeviceInfo, reason: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val changed = audioManager.setCommunicationDevice(device)
            Log.d(TAG, "Route -> ${device.type} ($reason), changed=$changed")
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            Log.d(TAG, "Route (legacy) -> ${device.type} ($reason)")
        }
    }

    private fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
    }

    private fun AudioDeviceInfo.isBluetoothCommunicationDevice(): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            type == AudioDeviceInfo.TYPE_HEARING_AID
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
