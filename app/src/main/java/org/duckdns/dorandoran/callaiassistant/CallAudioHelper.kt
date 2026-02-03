package org.duckdns.dorandoran.callaiassistant

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * 통화 중 오디오 라우팅 (스피커폰 등) 제어
 * Android 12+ 에서는 setSpeakerphoneOn 대신 setCommunicationDevice 사용
 */
object CallAudioHelper {

    /**
     * 통화 시작 시 오디오 모드 설정
     */
    fun setCallAudioMode(audioManager: AudioManager) {
        audioManager.mode = AudioManager.MODE_IN_CALL
    }

    /**
     * 통화 종료 시 오디오 모드 복원
     */
    fun restoreAudioMode(audioManager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    /**
     * 스피커폰 on/off 전환
     * @return 실제 적용된 스피커폰 상태
     */
    fun setSpeakerphone(audioManager: AudioManager, on: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                val devices = audioManager.availableCommunicationDevices
                val speaker = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    return audioManager.setCommunicationDevice(speaker)
                }
            } else {
                audioManager.clearCommunicationDevice()
                return true
            }
        }
        audioManager.isSpeakerphoneOn = on
        return audioManager.isSpeakerphoneOn
    }

    /**
     * 현재 스피커폰 상태 확인
     */
    fun isSpeakerOn(audioManager: AudioManager): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = audioManager.communicationDevice
            return device?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        return audioManager.isSpeakerphoneOn
    }
}
