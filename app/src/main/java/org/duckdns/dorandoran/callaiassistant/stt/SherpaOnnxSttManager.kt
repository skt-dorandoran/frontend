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
    private val onError: ((String) -> Unit)? = null,
    private val streamLabel: String = "generic"
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
    private var startJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    @Volatile
    private var isStarting = false

    fun initialize(modelDir: File) {
        try {
            val encoderFp32 = File(modelDir, "encoder-epoch-99-avg-1.onnx")
            val encoderInt8 = File(modelDir, "encoder-epoch-99-avg-1.int8.onnx")
            val decoderFp32 = File(modelDir, "decoder-epoch-99-avg-1.onnx")
            val decoderInt8 = File(modelDir, "decoder-epoch-99-avg-1.int8.onnx")
            val joinerFp32 = File(modelDir, "joiner-epoch-99-avg-1.onnx")
            val joinerInt8 = File(modelDir, "joiner-epoch-99-avg-1.int8.onnx")
            // Prefer fp32 for higher recognition accuracy; fallback to int8.
            val encoder = if (encoderFp32.exists()) encoderFp32 else encoderInt8
            val decoder = if (decoderFp32.exists()) decoderFp32 else decoderInt8
            val joiner = if (joinerFp32.exists()) joinerFp32 else joinerInt8
            val tokens = File(modelDir, "tokens.txt")
            val sttThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

            // sherpa-onnx 공식 예제 구조에 맞게 config 객체 생성
            val modelConfig = com.k2fsa.sherpa.onnx.OnlineModelConfig(
                transducer = com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    joiner = joiner.absolutePath
                ),
                tokens = tokens.absolutePath,
                numThreads = sttThreads,
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
                // Endpointing helps fast speaker turn-taking and stream reset.
                enableEndpoint = true,
                decodingMethod = "greedy_search",
                maxActivePaths = 4
            )
            recognizer = OnlineRecognizer(null, config)
            Log.i(
                "SherpaOnnxSttManager",
                "[$streamLabel] OnlineRecognizer 초기화 완료 (threads=$sttThreads, encoder=${encoder.name}, decoder=${decoder.name}, joiner=${joiner.name})"
            )
        } catch (e: Exception) {
            Log.e("SherpaOnnxSttManager", "모델 초기화 실패", e)
            onError?.invoke(e.message ?: "모델 초기화 실패")
        }
    }

    fun startStreaming() {
        if (isStarting || sttJob?.isActive == true) return
        isStarting = true
        startJob?.cancel()
        startJob = scope.launch {
            try {
                val modelDir = File(context.filesDir, "sherpa-onnx/sherpa-onnx-streaming-zipformer-korean-2024-06-16")
                val missing = !File(modelDir, "tokens.txt").exists() ||
                    !(File(modelDir, "encoder-epoch-99-avg-1.onnx").exists() ||
                        File(modelDir, "encoder-epoch-99-avg-1.int8.onnx").exists()) ||
                    !(File(modelDir, "decoder-epoch-99-avg-1.onnx").exists() ||
                        File(modelDir, "decoder-epoch-99-avg-1.int8.onnx").exists()) ||
                    !(File(modelDir, "joiner-epoch-99-avg-1.onnx").exists() ||
                        File(modelDir, "joiner-epoch-99-avg-1.int8.onnx").exists())
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
                        return@launch
                    }
                }
                if (recognizer == null) {
                    initialize(modelDir)
                }
                if (recognizer == null) {
                    onError?.invoke("Recognizer 초기화 실패")
                    return@launch
                }
                stream = recognizer!!.createStream()
                lastEmittedText = ""
                lastEmitAtMs = 0L
                hpPrevIn = 0f
                hpPrevOut = 0f
                agcGain = 1f
                mergedText = ""
                lastRawResultAtMs = 0L

                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                audioRecord = createBestEffortAudioRecord(sampleRate, channelConfig, audioFormat, minBufferSize * 2)
                if (audioRecord == null) {
                    onError?.invoke("AudioRecord 초기화 실패")
                    return@launch
                }
                audioRecord?.startRecording()
                if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    onError?.invoke("마이크 녹음 시작 실패")
                    audioRecord?.release()
                    audioRecord = null
                    return@launch
                }

                sttJob = launch {
                    val buffer = ShortArray(1024)
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
                Log.d("SherpaOnnxSttManager", "[$streamLabel] Streaming STT 시작 (AudioRecord)")
            } finally {
                isStarting = false
            }
        }
    }

    fun stopStreaming() {
        startJob?.cancel()
        startJob = null
        isStarting = false
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
        Log.d("SherpaOnnxSttManager", "[$streamLabel] Streaming STT 종료")
    }

    fun release() {
        stopStreaming()
        recognizer = null
    }

    private fun createBestEffortAudioRecord(
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSize: Int
    ): AudioRecord? {
        val preferredSources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in preferredSources) {
            try {
                val candidate = AudioRecord(
                    source,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i("SherpaOnnxSttManager", "AudioRecord source selected: $source")
                    return candidate
                }
                candidate.release()
            } catch (_: Exception) {
                // Try next source.
            }
        }
        return null
    }

    private fun preprocessMicForStt(input: ShortArray, read: Int): FloatArray {
        val out = FloatArray(read)
        val hpAlpha = 0.94f
        var prevIn = hpPrevIn
        var prevOut = hpPrevOut
        var energy = 0f
        var peak = 0f

        for (i in 0 until read) {
            val x = input[i].toFloat() / Short.MAX_VALUE
            val yHp = hpAlpha * (prevOut + x - prevIn)
            // Keep some low-frequency component to avoid losing mumbled consonants/vowels.
            val y = (yHp * 0.75f) + (x * 0.25f)
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
        agcGain = agcGain.coerceIn(1f, 12f)
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
        if (lastRawResultAtMs > 0L && now - lastRawResultAtMs > 900L) {
            mergedText = ""
        }
        lastRawResultAtMs = now

        val merged = mergeTranscript(mergedText, text)
        mergedText = merged

        if (merged == lastEmittedText) return
        // Always deliver append-only updates so final syllables are not dropped.
        // Only throttle rewrite-type fluctuations.
        val appendOnly = merged.startsWith(lastEmittedText)
        val cooldownPassed = now - lastEmitAtMs >= 120
        if (!appendOnly && !cooldownPassed) return

        lastEmittedText = merged
        lastEmitAtMs = now
        onResult(merged)
    }

    private fun mergeTranscript(previous: String, incoming: String): String {
        if (previous.isBlank()) return incoming
        if (incoming.startsWith(previous)) return incoming
        if (previous.startsWith(incoming)) return previous
        // Avoid speculative overlap concatenation: it can produce gibberish joins.
        return incoming
    }
}
