package org.duckdns.dorandoran.callaiassistant.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.audio.TtsAudioInjector

/**
 * Custom AudioDeviceModule: Native (C++) 레이어에서 마이크와 TTS를 믹싱
 * - 에코 캔슬러 활성화 유지
 * - TTS 음성을 Native 큐에 저장
 * - 마이크 샘플이 올 때마다 Native에서 믹싱
 */
class CustomAudioDeviceModule private constructor(
    private val context: Context,
    private val nativeAudioDeviceModule: AudioDeviceModule
) : AudioDeviceModule by nativeAudioDeviceModule {

    companion object {
        private const val TAG = "CustomAudioDeviceModule"
        private const val WEBRTC_SAMPLE_RATE = 48000 // WebRTC 권장 샘플레이트
        
        init {
            Log.i(TAG, "TtsAudioInjector ready")
        }
        
        /**
         * TTS PCM 데이터 주입 (Float를 Short로 변환 후 Native로 전달)
         */
        fun injectTtsPcm(samples: FloatArray, sampleRate: Int) {
            // Float → Short 변환
            val shortSamples = ShortArray(samples.size) { i ->
                (samples[i] * Short.MAX_VALUE).coerceIn(
                    Short.MIN_VALUE.toFloat(),
                    Short.MAX_VALUE.toFloat()
                ).toInt().toShort()
            }
            
            // 리샘플링 (TTS source -> WebRTC capture rate)
            val resampled = if (sampleRate != WEBRTC_SAMPLE_RATE) {
                resample(shortSamples, sampleRate, WEBRTC_SAMPLE_RATE)
            } else {
                shortSamples
            }

            // Push in 20ms chunks to reduce bursty queueing/latency artifacts.
            val chunkSamples = (WEBRTC_SAMPLE_RATE / 50).coerceAtLeast(1) // 20ms
            var offset = 0
            while (offset < resampled.size) {
                val len = minOf(chunkSamples, resampled.size - offset)
                TtsAudioInjector.nativePushPcm(resampled.copyOfRange(offset, offset + len))
                offset += len
            }
            Log.d(
                TAG,
                "✅ TTS PCM injected: ${resampled.size} samples @${WEBRTC_SAMPLE_RATE}Hz " +
                    "(native queue: ${TtsAudioInjector.nativeGetAvailable()})"
            )
        }
        
        /**
         * 간단한 리샘플링 (Linear interpolation)
         */
        private fun resample(input: ShortArray, inputRate: Int, outputRate: Int): ShortArray {
            val ratio = inputRate.toFloat() / outputRate
            val outputSize = (input.size / ratio).toInt()
            val output = ShortArray(outputSize)
            
            for (i in output.indices) {
                val srcIndex = i * ratio
                val srcIndexInt = srcIndex.toInt()
                if (srcIndexInt + 1 < input.size) {
                    val frac = srcIndex - srcIndexInt
                    output[i] = (input[srcIndexInt] * (1 - frac) + input[srcIndexInt + 1] * frac).toInt().toShort()
                } else if (srcIndexInt < input.size) {
                    output[i] = input[srcIndexInt]
                }
            }
            
            return output
        }
        
        /**
         * TTS 큐 초기화
         */
        fun clearTtsQueue() {
            TtsAudioInjector.nativeClear()
            Log.d(TAG, "TTS queue cleared")
        }
        
        /**
         * Builder
         */
        fun builder(context: Context): Builder {
            return Builder(context)
        }
    }
    
    class Builder(private val context: Context) {
        private var useHardwareAcousticEchoCanceler = true
        private var useHardwareNoiseSuppressor = true
        
        fun setUseHardwareAcousticEchoCanceler(use: Boolean): Builder {
            useHardwareAcousticEchoCanceler = use
            return this
        }
        
        fun setUseHardwareNoiseSuppressor(use: Boolean): Builder {
            useHardwareNoiseSuppressor = use
            return this
        }
        
        fun createAudioDeviceModule(): CustomAudioDeviceModule {
            // JavaAudioDeviceModule 생성
            val javaAudioModule = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(useHardwareAcousticEchoCanceler)
                .setUseHardwareNoiseSuppressor(useHardwareNoiseSuppressor)
                .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                    override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                        Log.e(TAG, "AudioRecord init error: $errorMessage")
                    }
                    
                    override fun onWebRtcAudioRecordStartError(
                        errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                        errorMessage: String?
                    ) {
                        Log.e(TAG, "AudioRecord start error: $errorMessage")
                    }
                    
                    override fun onWebRtcAudioRecordError(errorMessage: String?) {
                        Log.e(TAG, "AudioRecord error: $errorMessage")
                    }
                })
                .createAudioDeviceModule()
            
            return CustomAudioDeviceModule(context, javaAudioModule)
        }
    }
    
    override fun release() {
        nativeAudioDeviceModule.release()
    }
}
