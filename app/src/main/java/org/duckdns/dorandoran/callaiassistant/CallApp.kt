package org.duckdns.dorandoran.callaiassistant

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.duckdns.dorandoran.callaiassistant.stt.SherpaOnnxSttManager
import org.duckdns.dorandoran.callaiassistant.tts.TtsManager
import org.duckdns.dorandoran.callaiassistant.webrtc.CallSignalingManager
import java.io.File

class CallApp : Application() {

    val callSignalingManager: CallSignalingManager by lazy {
        CallSignalingManager(applicationContext)
    }
    private val prewarmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        prewarmScope.launch {
            // Prewarm TTS to reduce first utterance latency.
            TtsManager.prewarm(applicationContext)
            // Prewarm STT model loading once in background.
            try {
                val modelDir = File(filesDir, "sherpa-onnx/sherpa-onnx-streaming-zipformer-korean-2024-06-16")
                val requiredToken = File(modelDir, "tokens.txt")
                if (!requiredToken.exists()) {
                    org.duckdns.dorandoran.callaiassistant.util.AssetCopyUtil.copyAssetFolder(
                        applicationContext,
                        "sherpa-onnx/sherpa-onnx-streaming-zipformer-korean-2024-06-16",
                        modelDir
                    )
                }
                val warmup = SherpaOnnxSttManager(
                    context = applicationContext,
                    onResult = {},
                    onError = {},
                    streamLabel = "app-prewarm"
                )
                warmup.initialize(modelDir)
                warmup.release()
            } catch (_: Exception) {
                // Best effort: do not fail app startup.
            }
        }
    }
}
