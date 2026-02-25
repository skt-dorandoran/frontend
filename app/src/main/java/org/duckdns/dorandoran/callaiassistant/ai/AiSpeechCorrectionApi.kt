package org.duckdns.dorandoran.callaiassistant.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.ConversationHistoryItem
import org.duckdns.dorandoran.callaiassistant.util.NetworkUrlUtil
import org.json.JSONArray
import org.json.JSONObject

data class AiSpeechCorrectionResponse(
    val callId: String,
    val correctedText: String,
    val rawText: String,
    val confidence: Double,
    val processingTime: Long,
    val message: String,
    val timestamp: String
)

object AiSpeechCorrectionApi {
    private const val TAG = "AiSpeechCorrectionApi"

    suspend fun correctSpeech(
        callId: String,
        rawText: String,
        conversationHistory: List<ConversationHistoryItem>,
        phoneNumber: String
    ): AiSpeechCorrectionResponse? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val historyJson = JSONArray().apply {
                conversationHistory.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("role", item.role)
                            put("text", item.text)
                        }
                    )
                }
            }
            val payload = JSONObject().apply {
                put("callId", callId)
                put("rawText", rawText)
                put("conversationHistory", historyJson)
                put("phoneNumber", phoneNumber)
            }
            val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("${NetworkUrlUtil.DORANDORAN_HTTPS_BASE_URL}/api/v1/ai/correct-speech")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(
                        TAG,
                        "correct-speech failed: statusCode=${response.code}, body=$responseBody"
                    )
                    return@withContext null
                }
                val root = JSONObject(responseBody)
                AiSpeechCorrectionResponse(
                    callId = root.optString("callId"),
                    correctedText = root.optString("correctedText"),
                    rawText = root.optString("rawText"),
                    confidence = root.optDouble("confidence", 0.0),
                    processingTime = root.optLong("processingTime", 0L),
                    message = root.optString("message"),
                    timestamp = root.optString("timestamp")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "correct-speech exception: ${e.message}", e)
            null
        }
    }
}
