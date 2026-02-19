package org.duckdns.dorandoran.callaiassistant.webrtc

import android.content.Context
import android.media.AudioFormat
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        // WebRTC Android audio processing path typically runs at 48kHz.
        // Feeding 8kHz PCM here makes queued TTS drain ~6x too fast on the send path.
        private const val WEBRTC_SAMPLE_RATE = 48000
        @Volatile
        private var lastCaptureSampleRate: Int = WEBRTC_SAMPLE_RATE
        @Volatile
        private var lastCaptureChannelCount: Int = 1
        @Volatile
        private var micSamplesListener: ((data: ByteArray, sampleRate: Int, channelCount: Int, bitsPerSample: Int) -> Unit)? = null
        private val ttsInjectScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        @Volatile
        private var ttsInjectJob: Job? = null
        
        init {
            Log.i(TAG, "TtsAudioInjector ready")
        }
        
        /**
         * TTS PCM 데이터 주입 (Float를 Short로 변환 후 Native로 전달)
         */
        fun injectTtsPcm(samples: FloatArray, sampleRate: Int) {
            val ttsGain = 1.35f
            // Float → Short 변환
            val shortSamples = ShortArray(samples.size) { i ->
                ((samples[i] * ttsGain) * Short.MAX_VALUE).coerceIn(
                    Short.MIN_VALUE.toFloat(),
                    Short.MAX_VALUE.toFloat()
                ).toInt().toShort()
            }

            val targetSampleRate = lastCaptureSampleRate.takeIf { it in 8000..96000 } ?: WEBRTC_SAMPLE_RATE
            val targetChannelCount = lastCaptureChannelCount.coerceIn(1, 2)

            // 리샘플링 (TTS source -> 현재 WebRTC capture rate)
            val resampled = if (sampleRate != targetSampleRate) {
                resample(shortSamples, sampleRate, targetSampleRate)
            } else {
                shortSamples
            }
            // 믹서가 capture 샘플 수(len)를 기준으로 pop하므로 채널 수를 맞춘다.
            val channelMatched = if (targetChannelCount == 1) {
                resampled
            } else {
                val expanded = ShortArray(resampled.size * targetChannelCount)
                var dst = 0
                for (sample in resampled) {
                    repeat(targetChannelCount) {
                        expanded[dst++] = sample
                    }
                }
                expanded
            }

            val previousJob = ttsInjectJob
            ttsInjectJob = ttsInjectScope.launch {
                if (previousJob != null && previousJob.isActive) {
                    previousJob.cancelAndJoin()
                }
                // 새 TTS가 시작되면 이전 잔여 큐는 비워 꼬임을 방지한다.
                TtsAudioInjector.nativeClear()

                // Push in paced 20ms chunks to avoid native queue overflow/drop.
                val chunkSamples = ((targetSampleRate * targetChannelCount) / 50).coerceAtLeast(1) // 20ms
                val maxBufferedSamples = ((targetSampleRate * targetChannelCount) / 4).coerceAtLeast(chunkSamples) // 250ms

                var offset = 0
                var pushed = 0
                while (offset < channelMatched.size) {
                    while (TtsAudioInjector.nativeGetAvailable() > maxBufferedSamples) {
                        delay(5)
                    }
                    val len = minOf(chunkSamples, channelMatched.size - offset)
                    TtsAudioInjector.nativePushPcm(channelMatched.copyOfRange(offset, offset + len))
                    offset += len
                    pushed += len
                    delay(20)
                }

                Log.d(
                    TAG,
                    "✅ TTS PCM streamed: $pushed/${channelMatched.size} samples @${targetSampleRate}Hz ch=$targetChannelCount " +
                        "(source=$sampleRate Hz, native queue=${TtsAudioInjector.nativeGetAvailable()})"
                )
            }
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
            ttsInjectJob?.cancel()
            ttsInjectJob = null
            TtsAudioInjector.nativeClear()
            Log.d(TAG, "TTS queue cleared (inject job canceled)")
        }

        fun setMicSamplesListener(
            listener: ((data: ByteArray, sampleRate: Int, channelCount: Int, bitsPerSample: Int) -> Unit)?
        ) {
            micSamplesListener = listener
            Log.d(TAG, if (listener == null) "Mic samples listener cleared" else "Mic samples listener registered")
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
        private var audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        
        fun setUseHardwareAcousticEchoCanceler(use: Boolean): Builder {
            useHardwareAcousticEchoCanceler = use
            return this
        }
        
        fun setUseHardwareNoiseSuppressor(use: Boolean): Builder {
            useHardwareNoiseSuppressor = use
            return this
        }

        fun setAudioSource(source: Int): Builder {
            audioSource = source
            return this
        }
        
        fun createAudioDeviceModule(): CustomAudioDeviceModule {
            // JavaAudioDeviceModule 생성
            val javaAudioModule = JavaAudioDeviceModule.builder(context)
                .setAudioSource(audioSource)
                .setSamplesReadyCallback { samples ->
                    val data = samples.data ?: return@setSamplesReadyCallback
                    lastCaptureSampleRate = samples.sampleRate
                    lastCaptureChannelCount = samples.channelCount.coerceAtLeast(1)
                    micSamplesListener?.invoke(
                        data,
                        samples.sampleRate,
                        samples.channelCount,
                        if (samples.audioFormat == AudioFormat.ENCODING_PCM_16BIT) 16 else 0
                    )
                }
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
