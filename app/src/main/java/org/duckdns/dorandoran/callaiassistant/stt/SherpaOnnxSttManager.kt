package org.duckdns.dorandoran.callaiassistant.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import java.io.File

/**
 * sherpa-onnx 기반 Streaming STT 매니저 (한국어 zipformer 모델)
 * - 모델 파일은 assets/sherpa-onnx/ 에 위치해야 함
 */
class SherpaOnnxSttManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: ((String) -> Unit)? = null
) {
    private var lastEmittedText: String = ""
    private var lastEmitAtMs: Long = 0L
    private var hpPrevIn: Float = 0f
    private var hpPrevOut: Float = 0f
    private var agcGain: Float = 1f
    private var mergedText: String = ""
    private var lastRawResultAtMs: Long = 0L

    fun createStream(): OnlineStream? {
        return recognizer?.createStream()
    }

    fun processStream(stream: OnlineStream) {
        if (recognizer != null) {
            while (recognizer!!.isReady(stream)) {
                recognizer!!.decode(stream)
                val result = recognizer!!.getResult(stream)
                if (result.text.isNotBlank()) {
                    emitStableResult(result.text)
                }
            }
        }
    }
    internal var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var sttJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun initialize(modelDir: File) {
        try {
            val encoder = File(modelDir, "encoder-epoch-99-avg-1.int8.onnx")
            val decoder = File(modelDir, "decoder-epoch-99-avg-1.onnx")
            val joiner = File(modelDir, "joiner-epoch-99-avg-1.int8.onnx")
            val tokens = File(modelDir, "tokens.txt")

            // sherpa-onnx 공식 예제 구조에 맞게 config 객체 생성
            val modelConfig = com.k2fsa.sherpa.onnx.OnlineModelConfig(
                transducer = com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    joiner = joiner.absolutePath
                ),
                tokens = tokens.absolutePath,
                numThreads = 2,
                debug = false
            )
            val featConfig = com.k2fsa.sherpa.onnx.FeatureConfig(
                sampleRate = 16000,
                featureDim = 80
            )
            val endpointConfig = com.k2fsa.sherpa.onnx.EndpointConfig()
            val lmConfig = com.k2fsa.sherpa.onnx.OnlineLMConfig()
            val ctcFstDecoderConfig = com.k2fsa.sherpa.onnx.OnlineCtcFstDecoderConfig()

            val config = OnlineRecognizerConfig(
                modelConfig = modelConfig,
                lmConfig = lmConfig,
                featConfig = featConfig,
                ctcFstDecoderConfig = ctcFstDecoderConfig,
                endpointConfig = endpointConfig,
                // Local/remote streaming text is merged on UI side; disabling endpoint
                // reduces aggressive sentence splits.
                enableEndpoint = false,
                decodingMethod = "greedy_search",
                maxActivePaths = 4
            )
            recognizer = OnlineRecognizer(null, config)
            Log.i("SherpaOnnxSttManager", "OnlineRecognizer 초기화 완료")
        } catch (e: Exception) {
            Log.e("SherpaOnnxSttManager", "모델 초기화 실패", e)
            onError?.invoke(e.message ?: "모델 초기화 실패")
        }
    }

    fun startStreaming() {
        val modelDir = File(context.filesDir, "sherpa-onnx/sherpa-onnx-streaming-zipformer-korean-2024-06-16")
        // 모델 파일이 없으면 assets에서 복사
        val requiredFiles = listOf(
            "encoder-epoch-99-avg-1.int8.onnx",
            "decoder-epoch-99-avg-1.onnx",
            "joiner-epoch-99-avg-1.int8.onnx",
            "tokens.txt"
        )
        val missing = requiredFiles.any { !File(modelDir, it).exists() }
        if (missing) {
            try {
                org.duckdns.dorandoran.callaiassistant.util.AssetCopyUtil.copyAssetFolder(
                    context,
                    "sherpa-onnx/sherpa-onnx-streaming-zipformer-korean-2024-06-16",
                    modelDir
                )
                Log.i("SherpaOnnxSttManager", "모델 파일 assets에서 복사 완료")
            } catch (e: Exception) {
                Log.e("SherpaOnnxSttManager", "모델 파일 복사 실패", e)
                onError?.invoke("모델 파일 복사 실패: ${e.message}")
                return
            }
        }
        if (recognizer == null) {
            initialize(modelDir)
        }
        if (recognizer == null) {
            onError?.invoke("Recognizer 초기화 실패")
            return
        }
        stream = recognizer!!.createStream()
        lastEmittedText = ""
        lastEmitAtMs = 0L
        hpPrevIn = 0f
        hpPrevOut = 0f
        agcGain = 1f
        mergedText = ""
        lastRawResultAtMs = 0L

        // AudioRecord 설정 (16kHz, MONO, PCM 16bit)
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize * 2
        )
        audioRecord?.startRecording()

        sttJob = scope.launch {
            val buffer = ShortArray(2048)
            try {
                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val pcm = preprocessMicForStt(buffer, read)
                        stream?.acceptWaveform(pcm, sampleRate)
                    }
                    if (recognizer!!.isReady(stream!!)) {
                        while (recognizer!!.isReady(stream!!)) {
                            recognizer!!.decode(stream!!)
                            val result = recognizer!!.getResult(stream!!)
                            if (result.text.isNotBlank()) {
                                emitStableResult(result.text)
                            }
                        }
                    }
                    kotlinx.coroutines.delay(10)
                }
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "STT 오류")
            }
        }
        Log.d("SherpaOnnxSttManager", "Streaming STT 시작 (AudioRecord)")
    }

    fun stopStreaming() {
        sttJob?.cancel()
        sttJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        stream?.release()
        stream = null
        lastEmittedText = ""
        lastEmitAtMs = 0L
        hpPrevIn = 0f
        hpPrevOut = 0f
        agcGain = 1f
        mergedText = ""
        lastRawResultAtMs = 0L
        Log.d("SherpaOnnxSttManager", "Streaming STT 종료")
    }

    fun release() {
        stopStreaming()
        recognizer = null
    }

    private fun preprocessMicForStt(input: ShortArray, read: Int): FloatArray {
        val out = FloatArray(read)
        val hpAlpha = 0.973f
        var prevIn = hpPrevIn
        var prevOut = hpPrevOut
        var energy = 0f
        var peak = 0f

        for (i in 0 until read) {
            val x = input[i].toFloat() / Short.MAX_VALUE
            val y = hpAlpha * (prevOut + x - prevIn)
            out[i] = y
            prevIn = x
            prevOut = y
            energy += y * y
            val absY = kotlin.math.abs(y)
            if (absY > peak) peak = absY
        }
        hpPrevIn = prevIn
        hpPrevOut = prevOut

        // Adaptive gain for low-volume speech.
        val rms = kotlin.math.sqrt((energy / read).coerceAtLeast(1e-9f))
        // Do not hard-drop low-level frames: quiet TTS gets removed otherwise.
        val veryLowLevel = peak < 0.010f && rms < 0.005f
        val targetRms = when {
            rms < 0.012f -> 0.16f
            rms < 0.025f -> 0.13f
            rms < 0.05f -> 0.11f
            else -> 0.10f
        }
        var desiredGain = (targetRms / rms).coerceIn(1f, 12f)
        if (peak > 1e-6f) {
            desiredGain = minOf(desiredGain, 0.97f / peak)
        }
        // Moderate AGC: avoid distortion while still lifting quiet speech.
        val smooth = if (desiredGain > agcGain) 0.25f else 0.08f
        agcGain = agcGain + (desiredGain - agcGain) * smooth
        if (veryLowLevel) {
            agcGain = maxOf(agcGain, 1.5f)
        }
        for (i in out.indices) {
            out[i] = (out[i] * agcGain).coerceIn(-1f, 1f)
        }
        return out
    }

    private fun emitStableResult(raw: String) {
        val text = raw.trim()
        if (text.isBlank()) return
        val now = System.currentTimeMillis()

        // If decoder restarts after a pause, allow a clean sentence restart.
        if (lastRawResultAtMs > 0L && now - lastRawResultAtMs > 2200L) {
            mergedText = ""
        }
        lastRawResultAtMs = now

        val merged = mergeTranscript(mergedText, text)
        mergedText = merged

        // Skip tiny fluctuations that cause choppy bubble updates.
        val changedEnough = kotlin.math.abs(merged.length - lastEmittedText.length) >= 2 ||
            !merged.startsWith(lastEmittedText)
        val cooldownPassed = now - lastEmitAtMs >= 180
        if (merged == lastEmittedText) return
        if (!changedEnough && !cooldownPassed) return

        lastEmittedText = merged
        lastEmitAtMs = now
        onResult(merged)
    }

    private fun mergeTranscript(previous: String, incoming: String): String {
        if (previous.isBlank()) return incoming
        if (incoming.startsWith(previous)) return incoming
        if (previous.startsWith(incoming)) return previous
        if (previous.contains(incoming)) return previous

        var overlap = 0
        val max = minOf(previous.length, incoming.length)
        for (k in max downTo 1) {
            if (previous.endsWith(incoming.substring(0, k))) {
                overlap = k
                break
            }
        }
        if (overlap > 0) {
            return previous + incoming.substring(overlap)
        }
        return incoming
    }
}
