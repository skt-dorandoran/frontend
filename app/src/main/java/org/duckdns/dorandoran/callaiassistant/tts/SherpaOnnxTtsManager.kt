package org.duckdns.dorandoran.callaiassistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.duckdns.dorandoran.callaiassistant.webrtc.CustomAudioDeviceModule
import java.io.File
import java.io.FileOutputStream

/**
 * sherpa-onnx 기반 Espeak TTS 관리
 * - espeak-ng-data 사용 (한국어 지원)
 * - PCM 샘플 생성 및 WebRTC 마이크 스트림에 직접 주입
 * - 스피커 출력 없음 (에코 방지)
 */
object SherpaOnnxTtsManager {

    private const val TAG = "SherpaOnnxTtsManager"
    
    @Volatile
    private var tts: OfflineTts? = null
    
    @Volatile
    private var isInitialized = false
    
    @Volatile
    private var initFailed = false
    
    @Volatile
    private var currentAudioTrack: AudioTrack? = null
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Espeak TTS 초기화
     */
    fun initialize(context: Context?, onReady: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (isInitialized && tts != null) {
            onReady()
            return
        }
        
        if (initFailed) {
            Log.w(TAG, "TTS initialization previously failed, skipping")
            onReady()
            return
        }
        
        if (context == null) {
            Log.w(TAG, "Cannot initialize without context")
            initFailed = true
            onReady()
            return
        }
        
        scope.launch {
            try {
                Log.d(TAG, "Initializing VITS TTS...")
                
                // 1. espeak-ng-data 복사
                val dataDir = File(context.filesDir, "espeak-ng-data")
                if (!dataDir.exists()) {
                    Log.d(TAG, "Copying espeak-ng-data...")
                    copyAssets(context, "tts/espeak-ng-data", dataDir)
                    Log.d(TAG, "espeak-ng-data copied")
                } else {
                    Log.d(TAG, "espeak-ng-data exists")
                }
                
                // 2. VITS 모델 파일들 복사
                val modelFile = File(context.filesDir, "ko_KO-kss_low.onnx")
                val tokensFile = File(context.filesDir, "tokens.txt")
                
                if (!modelFile.exists()) {
                    Log.d(TAG, "Copying VITS model (60MB)...")
                    context.assets.open("tts/ko_KO-kss_low.onnx").use { input ->
                        FileOutputStream(modelFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Model copied: ${modelFile.absolutePath}")
                }
                
                if (!tokensFile.exists()) {
                    context.assets.open("tts/tokens.txt").use { input ->
                        FileOutputStream(tokensFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Tokens copied")
                }
                
                // 3. VITS 초기화
                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    lexicon = "",
                    tokens = tokensFile.absolutePath,
                    dataDir = dataDir.absolutePath,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
                
                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
                
                val config = OfflineTtsConfig(
                    model = modelConfig,
                    ruleFsts = "",
                    maxNumSentences = 1
                )
                
                Log.d(TAG, "Creating OfflineTts instance...")
                val ttsInstance = OfflineTts(
                    assetManager = null,  // 파일 경로 사용
                    config = config
                )
                
                if (ttsInstance == null) {
                    throw Exception("OfflineTts creation failed")
                }
                
                tts = ttsInstance
                isInitialized = true
                
                val sampleRate = tts?.sampleRate() ?: 0
                Log.i(TAG, "VITS TTS initialized - sample rate: $sampleRate Hz")
                onReady()
                
            } catch (e: Exception) {
                Log.e(TAG, "TTS initialization failed", e)
                isInitialized = false
                initFailed = true
                tts = null
                onError(e.message ?: "Unknown error")
                onReady()
            }
        }
    }

    /**
     * 텍스트를 음성으로 변환하여 로컬 스피커로 재생
     * - 로컬 스피커 출력 (사용자가 직접 들음)
     * - WebRTC 마이크 스트림에도 주입 (상대방도 들음)
     */
    fun speak(text: String, audioManager: AudioManager? = null, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        
        if (initFailed || !isInitialized || tts == null) {
            Log.d(TAG, "TTS not available, skipping speech generation")
            onDone?.invoke()
            return
        }
        
        scope.launch {
            try {
                Log.d(TAG, "Generating speech for: $text")
                
                // sherpa-onnx로 PCM 샘플 생성
                val audio = tts!!.generate(
                    text = text,
                    sid = 0,
                    speed = 1.0f
                )
                
                val samples = audio.samples
                val sampleRate = audio.sampleRate
                
                Log.d(TAG, "Generated ${samples.size} samples at $sampleRate Hz")
                
                // 1. CustomAudioDeviceModule에 TTS PCM 주입 (상대방에게 전송) - 먼저!
                CustomAudioDeviceModule.injectTtsPcm(samples, sampleRate)
                Log.i(TAG, "TTS PCM injected into WebRTC microphone stream")
                
                // 2. 로컬 스피커로 재생 (비동기) - 사용자 자신이 들음
                scope.launch {
                    playToSpeaker(samples, sampleRate)
                    Log.i(TAG, "TTS played to local speaker")
                    
                    // 재생 완료 후 onDone 호출
                    withContext(Dispatchers.Main) {
                        onDone?.invoke()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "TTS generation failed", e)
                withContext(Dispatchers.Main) {
                    onDone?.invoke()
                }
            }
        }
    }
    
    /**
     * TTS 오디오를 로컬 스피커로 재생
     */
    private fun playToSpeaker(samples: FloatArray, sampleRate: Int) {
        try {
            // Float → Short 변환 (PCM_16BIT)
            val pcmBuffer = ShortArray(samples.size) { i ->
                (samples[i] * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(pcmBuffer.size * 2)
            
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            
            val audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            
            synchronized(this) {
                currentAudioTrack?.stop()
                currentAudioTrack?.release()
                currentAudioTrack = audioTrack
            }
            
            Log.d(TAG, "AudioTrack created: sampleRate=$sampleRate, bufferSize=$bufferSize, samples=${pcmBuffer.size}")
            
            audioTrack.play()
            
            // PCM 데이터 쓰기
            var offset = 0
            while (offset < pcmBuffer.size) {
                val written = audioTrack.write(
                    pcmBuffer,
                    offset,
                    (pcmBuffer.size - offset).coerceAtMost(bufferSize / 2)
                )
                if (written < 0) {
                    Log.e(TAG, "AudioTrack write error: $written")
                    break
                }
                offset += written
            }
            
            Log.d(TAG, "AudioTrack wrote $offset samples")
            
            // 재생 완료 대기 (샘플 수 / 샘플레이트 = 재생 시간)
            val durationMs = (pcmBuffer.size * 1000L / sampleRate) + 200  // +200ms 여유
            Log.d(TAG, "Waiting ${durationMs}ms for playback to complete...")
            Thread.sleep(durationMs)
            
            audioTrack.stop()
            audioTrack.release()
            synchronized(this) {
                if (currentAudioTrack == audioTrack) {
                    currentAudioTrack = null
                }
            }
            Log.d(TAG, "AudioTrack playback completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play TTS to speaker", e)
        }
    }

    /**
     * assets 폴더를 내부 스토리지로 재귀 복사
     */
    private fun copyAssets(context: Context, assetPath: String, destDir: File) {
        try {
            val assets = context.assets.list(assetPath) ?: emptyArray()
            
            if (assets.isEmpty()) {
                // 파일인 경우
                context.assets.open(assetPath).use { input ->
                    val destFile = File(destDir.parent, destDir.name)
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // 디렉토리인 경우
                destDir.mkdirs()
                for (asset in assets) {
                    val childPath = "$assetPath/$asset"
                    val childDest = File(destDir, asset)
                    copyAssets(context, childPath, childDest)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying assets: $assetPath", e)
        }
    }

    /**
     * TTS 정지 및 리소스 해제
     */
    fun shutdown() {
        try {
            // 재생 중인 AudioTrack 즉시 중단
            synchronized(this) {
                currentAudioTrack?.stop()
                currentAudioTrack?.release()
                currentAudioTrack = null
            }
            Log.d(TAG, "AudioTrack stopped and released")
            
            tts?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error during shutdown", e)
        }
        tts = null
        isInitialized = false  // 다시 초기화할 수 있도록 플래그 초기화
        initFailed = false     // 초기화 실패 플래그도 리셋
        Log.d(TAG, "TTS Manager shutdown complete - ready for re-initialization")
    }
}
