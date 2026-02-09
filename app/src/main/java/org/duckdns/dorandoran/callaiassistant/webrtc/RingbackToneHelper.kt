package org.duckdns.dorandoran.callaiassistant.webrtc

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * 통화 연결 중 재생되는 링백톤(통화연결음) 관리
 */
class RingbackToneHelper {

    companion object {
        private const val TAG = "RingbackToneHelper"
    }

    private var toneGenerator: ToneGenerator? = null

    fun start() {
        try {
            release()
            // STREAM_VOICE_CALL 사용 - 통화 볼륨으로 재생
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, ToneGenerator.MAX_VOLUME)
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_NETWORK_USA_RINGBACK, -1)
            Log.d(TAG, "Ringback tone started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringback tone", e)
        }
    }

    fun stop() {
        try {
            toneGenerator?.stopTone()
            Log.d(TAG, "Ringback tone stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop ringback tone", e)
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release tone generator", e)
        }
    }
}
