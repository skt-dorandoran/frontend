package org.duckdns.dorandoran.callaiassistant.stt

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class RealtimeSttPayload(
    val text: String,
    val confidence: Double? = null,
    val timestamp: String? = null,
    val start: Double? = null,
    val duration: Double? = null,
    val speechFinal: Boolean = false
)

data class RealtimeComprehensionPayload(
    val status: String,
    val failureCount: Int?,
    val threshold: Int?,
    val enableAiCorrection: Boolean
)

class RealtimeTranscribeWsClient(
    private val tag: String,
    private val silenceThresholdSeconds: Double? = null,
    private val callId: String = "call_${UUID.randomUUID().toString().replace("-", "").take(12)}",
    private val onInterim: (RealtimeSttPayload) -> Unit = {},
    private val onFinal: (RealtimeSttPayload) -> Unit = {},
    private val onSilenceDetected: (Double) -> Unit = {},
    private val onComprehension: (RealtimeComprehensionPayload) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    companion object {
        private const val BASE_URL = "https://dorandoran.dev"
        private const val WS_URL = "wss://dorandoran.dev/api/v1/speech/transcribe/ws"
        private const val TARGET_SAMPLE_RATE = 16000
    }

    private val client by lazy { OkHttpClient() }
    @Volatile
    private var ws: WebSocket? = null
    @Volatile
    private var connected: Boolean = false

    fun connect() {
        if (ws != null) return
        val request = Request.Builder().url(WS_URL).build()
        ws = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    connected = true
                    val initPayload = JSONObject().apply {
                        put("sampleRate", TARGET_SAMPLE_RATE)
                        put("callId", callId)
                        if (silenceThresholdSeconds != null) {
                            put("silenceThreshold", silenceThresholdSeconds)
                        }
                    }
                    webSocket.send(initPayload.toString())
                    Log.d(tag, "STT WS connected: $BASE_URL")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleTextMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    // 서버는 현재 텍스트 이벤트만 사용
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    connected = false
                    Log.d(tag, "STT WS closing: $code/$reason")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    connected = false
                    ws = null
                    Log.d(tag, "STT WS closed: $code/$reason")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    connected = false
                    ws = null
                    val reason = t.message ?: "WebSocket failure"
                    Log.e(tag, "STT WS failure: $reason", t)
                    onError(reason)
                }
            }
        )
    }

    fun disconnect() {
        ws?.send(JSONObject().put("type", "finalize").toString())
        ws?.close(1000, "client_close")
        ws = null
        connected = false
    }

    fun sendPcm16Mono16k(frameBytes: ByteArray) {
        if (frameBytes.isEmpty() || !connected) return
        ws?.send(frameBytes.toByteString())
    }

    fun sendPcm16(input: ByteArray, sampleRate: Int, channels: Int) {
        if (input.isEmpty() || sampleRate <= 0 || channels <= 0 || !connected) return
        val mono = pcm16BytesToMonoShort(input, channels)
        if (mono.isEmpty()) return
        val normalized = if (sampleRate != TARGET_SAMPLE_RATE) {
            resampleShortPcm(mono, sampleRate, TARGET_SAMPLE_RATE)
        } else {
            mono
        }
        if (normalized.isEmpty()) return
        sendPcm16Mono16k(shortArrayToLittleEndianBytes(normalized))
    }

    private fun handleTextMessage(raw: String) {
        try {
            val data = JSONObject(raw)
            val type = data.optString("type")
            val status = data.optString("status")
            when (type) {
                "interim" -> onInterim(
                    RealtimeSttPayload(
                        text = data.optString("text"),
                        confidence = data.optDouble("confidence").takeIf { !it.isNaN() },
                        timestamp = data.optString("timestamp"),
                        start = data.optDouble("start").takeIf { !it.isNaN() },
                        duration = data.optDouble("duration").takeIf { !it.isNaN() },
                        speechFinal = data.optBoolean("speech_final", false)
                    )
                )
                "final" -> onFinal(
                    RealtimeSttPayload(
                        text = data.optString("text"),
                        confidence = data.optDouble("confidence").takeIf { !it.isNaN() },
                        timestamp = data.optString("timestamp"),
                        start = data.optDouble("start").takeIf { !it.isNaN() },
                        duration = data.optDouble("duration").takeIf { !it.isNaN() },
                        speechFinal = data.optBoolean("speech_final", false)
                    )
                )
                "silence_detected" -> onSilenceDetected(data.optDouble("silenceDuration", 0.0))
                "comprehension_check" -> onComprehension(
                    RealtimeComprehensionPayload(
                        status = data.optString("status", "ok"),
                        failureCount = data.optInt("failureCount").takeIf { it >= 0 },
                        threshold = data.optInt("threshold").takeIf { it >= 0 },
                        enableAiCorrection = data.optBoolean("enableAiCorrection", false)
                    )
                )
                "error" -> onError(
                    data.optString("message")
                        .ifBlank { data.optString("text") }
                        .ifBlank { "STT server error" }
                )
                else -> {
                    // 일부 이해실패 응답은 type 없이 status만 내려온다.
                    if (status.isNotBlank()) {
                        onComprehension(
                            RealtimeComprehensionPayload(
                                status = status,
                                failureCount = data.optInt("failureCount").takeIf { it >= 0 },
                                threshold = data.optInt("threshold").takeIf { it >= 0 },
                                enableAiCorrection = data.optBoolean("enableAiCorrection", false)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse STT WS message: $raw", e)
        }
    }

    private fun pcm16BytesToMonoShort(audioData: ByteArray, numberOfChannels: Int): ShortArray {
        if (audioData.isEmpty() || numberOfChannels <= 0) return ShortArray(0)
        val shortBuf = ByteBuffer
            .wrap(audioData)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        if (!shortBuf.hasRemaining()) return ShortArray(0)

        val input = ShortArray(shortBuf.remaining())
        shortBuf.get(input)
        if (input.isEmpty()) return ShortArray(0)

        if (numberOfChannels == 1) return input
        val frameCount = input.size / numberOfChannels
        if (frameCount <= 0) return ShortArray(0)
        val mixed = ShortArray(frameCount)
        var src = 0
        for (i in 0 until frameCount) {
            var sum = 0
            for (ch in 0 until numberOfChannels) {
                sum += input[src + ch].toInt()
            }
            mixed[i] = (sum / numberOfChannels).toShort()
            src += numberOfChannels
        }
        return mixed
    }

    private fun resampleShortPcm(input: ShortArray, inputRate: Int, outputRate: Int): ShortArray {
        if (input.isEmpty() || inputRate == outputRate) return input
        val ratio = inputRate.toDouble() / outputRate.toDouble()
        val outSize = kotlin.math.max(1, (input.size / ratio).toInt())
        val output = ShortArray(outSize)
        for (i in output.indices) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            if (srcIdx + 1 < input.size) {
                val frac = (srcPos - srcIdx).toFloat()
                val a = input[srcIdx].toFloat()
                val b = input[srcIdx + 1].toFloat()
                output[i] = (a * (1f - frac) + b * frac).toInt().toShort()
            } else {
                output[i] = input[input.lastIndex]
            }
        }
        return output
    }

    private fun shortArrayToLittleEndianBytes(input: ShortArray): ByteArray {
        val bytes = ByteArray(input.size * 2)
        var idx = 0
        input.forEach { sample ->
            bytes[idx] = (sample.toInt() and 0xFF).toByte()
            bytes[idx + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            idx += 2
        }
        return bytes
    }
}
