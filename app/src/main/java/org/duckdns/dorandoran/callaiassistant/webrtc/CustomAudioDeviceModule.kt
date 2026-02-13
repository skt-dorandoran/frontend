package org.duckdns.dorandoran.callaiassistant.webrtc

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Custom AudioDeviceModule: 마이크 입력에 TTS PCM을 직접 믹싱
 * - 에코 캔슬러 활성화 유지
 * - TTS 음성을 마이크 스트림에 주입
 * - 스피커 출력은 마이크로 들어가지 않음
 */
class CustomAudioDeviceModule private constructor(
    private val context: Context,
    private val nativeAudioDeviceModule: AudioDeviceModule
) : AudioDeviceModule by nativeAudioDeviceModule {

    companion object {
        private const val TAG = "CustomAudioDeviceModule"
        private const val WEBRTC_SAMPLE_RATE = 8000 // WebRTC 실제 사용 샘플레이트 (로그 확인)
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE_MS = 10 // 10ms 프레임
        private const val SAMPLES_PER_FRAME = WEBRTC_SAMPLE_RATE * FRAME_SIZE_MS / 1000 // 80 samples
        
        // TTS PCM 큐 (Float 샘플)
        private val ttsPcmQueue = ConcurrentLinkedQueue<FloatArray>()
        private var ttsSampleRate = 22050 // Espeak 기본값
        private var ttsBufferIndex = 0
        private var currentTtsBuffer: FloatArray? = null
        
        /**
         * TTS PCM 데이터 주입 (WebRTC sample rate로 리샘플링)
         */
        fun injectTtsPcm(samples: FloatArray, sampleRate: Int) {
            ttsSampleRate = sampleRate
            // WebRTC가 8000Hz를 사용하므로 8000Hz로 리샘플링
            val resampled = if (sampleRate != WEBRTC_SAMPLE_RATE) {
                resample(samples, sampleRate, WEBRTC_SAMPLE_RATE)
            } else {
                samples
            }
            ttsPcmQueue.offer(resampled)
            Log.d(TAG, "TTS PCM injected: ${resampled.size} samples at $WEBRTC_SAMPLE_RATE Hz (resampled from $sampleRate Hz)")
        }
        
        /**
         * TTS 큐 초기화 (통화 종료 시 호출)
         */
        fun clearTtsQueue() {
            ttsPcmQueue.clear()
            currentTtsBuffer = null
            ttsBufferIndex = 0
            Log.d(TAG, "TTS queue cleared")
        }
        
        /**
         * 간단한 리샘플링 (Linear interpolation)
         */
        private fun resample(input: FloatArray, inputRate: Int, outputRate: Int): FloatArray {
            val ratio = inputRate.toFloat() / outputRate
            val outputSize = (input.size / ratio).toInt()
            val output = FloatArray(outputSize)
            
            for (i in output.indices) {
                val srcIndex = i * ratio
                val srcIndexInt = srcIndex.toInt()
                if (srcIndexInt + 1 < input.size) {
                    val frac = srcIndex - srcIndexInt
                    output[i] = input[srcIndexInt] * (1 - frac) + input[srcIndexInt + 1] * frac
                } else if (srcIndexInt < input.size) {
                    output[i] = input[srcIndexInt]
                }
            }
            
            return output
        }
        
        /**
         * 다음 TTS 샘플 가져오기
         */
        private fun getNextTtsSample(): Float {
            var buffer = currentTtsBuffer
            
            // 현재 버퍼가 없거나 다 사용했으면 새 버퍼 가져오기
            if (buffer == null || ttsBufferIndex >= buffer.size) {
                buffer = ttsPcmQueue.poll()
                currentTtsBuffer = buffer
                ttsBufferIndex = 0
                
                if (buffer == null) {
                    return 0f // TTS 데이터 없음
                }
            }
            
            return buffer[ttsBufferIndex++]
        }
        
        /**
         * Builder
         */
        fun builder(context: Context): Builder {
            return Builder(context)
        }
        
        /**
         * 마이크 샘플에 TTS PCM 믹싱 (static 메서드로 이동)
         */
        fun mixTtsIntoMicSamples(samples: JavaAudioDeviceModule.AudioSamples) {
            // TTS 큐가 비어있으면 스킵
            if (ttsPcmQueue.isEmpty() && currentTtsBuffer == null) {
                return
            }
            
            val data = samples.data
            val channelCount = samples.channelCount
            val sampleCount = data.size / channelCount / 2 // 16-bit = 2 bytes
            
            Log.d(TAG, "Mixing TTS: queue size=${ttsPcmQueue.size}, currentBuffer=${currentTtsBuffer?.size}, sampleCount=$sampleCount, channelCount=$channelCount")
            
            // ByteBuffer를 ShortBuffer로 변환
            val byteBuffer = ByteBuffer.wrap(data)
            byteBuffer.order(java.nio.ByteOrder.nativeOrder())
            val shortBuffer = byteBuffer.asShortBuffer()
            
            var mixedCount = 0
            
            // 각 샘플에 TTS 믹싱
            for (i in 0 until sampleCount) {
                for (ch in 0 until channelCount) {
                    val index = i * channelCount + ch
                    val micSample = shortBuffer.get(index)
                    val ttsSample = getNextTtsSample()
                    
                    if (ttsSample != 0f) {
                        mixedCount++
                    }
                    
                    // 믹싱: 마이크 + TTS (간단한 합산, clipping 방지)
                    val mixed = (micSample + ttsSample * Short.MAX_VALUE).coerceIn(
                        Short.MIN_VALUE.toFloat(),
                        Short.MAX_VALUE.toFloat()
                    ).toInt().toShort()
                    
                    shortBuffer.put(index, mixed)
                }
            }
            
            if (mixedCount > 0) {
                Log.d(TAG, "Mixed $mixedCount TTS samples into microphone stream")
            }
            
            // 수정된 데이터를 원본 배열에 반영
            byteBuffer.rewind()
            byteBuffer.get(data)
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
            // 기본 JavaAudioDeviceModule 생성 (에코 캔슬러 활성화)
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
                .setSamplesReadyCallback(object : JavaAudioDeviceModule.SamplesReadyCallback {
                    override fun onWebRtcAudioRecordSamplesReady(samples: JavaAudioDeviceModule.AudioSamples?) {
                        samples ?: return
                        
                        // 마이크 샘플에 TTS PCM 믹싱 (companion object의 static 메서드 호출)
                        Log.d(TAG, "SamplesReadyCallback called: ${samples.data.size} bytes")
                        CustomAudioDeviceModule.mixTtsIntoMicSamples(samples)
                    }
                })
                .createAudioDeviceModule()
            
            return CustomAudioDeviceModule(context, javaAudioModule)
        }
    }
    
    override fun release() {
        nativeAudioDeviceModule.release()
        ttsPcmQueue.clear()
        currentTtsBuffer = null
        ttsBufferIndex = 0
    }
}
