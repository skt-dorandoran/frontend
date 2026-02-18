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
    fun createStream(): OnlineStream? {
        return recognizer?.createStream()
    }

    fun processStream(stream: OnlineStream, viewModel: org.duckdns.dorandoran.callaiassistant.ui.viewmodel.CallViewModel?) {
        if (recognizer != null && recognizer!!.isReady(stream)) {
            recognizer!!.decode(stream)
            val result = recognizer!!.getResult(stream)
            if (result.text.isNotBlank()) {
                viewModel?.addRemoteMessage(result.text)
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
                enableEndpoint = true,
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

        // AudioRecord 설정 (16kHz, MONO, PCM 16bit)
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
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
                        // ShortArray → FloatArray 변환 후 PCM 전달
                        val pcm = buffer.copyOf(read).map { it.toFloat() / Short.MAX_VALUE }.toFloatArray()
                        stream?.acceptWaveform(pcm, sampleRate)
                    }
                    if (recognizer!!.isReady(stream!!)) {
                        recognizer!!.decode(stream!!)
                        val result = recognizer!!.getResult(stream!!)
                        if (result.text.isNotBlank()) {
                            onResult(result.text)
                        }
                    }
                    kotlinx.coroutines.delay(40) // 40ms 단위로 polling
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
        Log.d("SherpaOnnxSttManager", "Streaming STT 종료")
    }

    fun release() {
        stopStreaming()
        recognizer = null
    }
}
