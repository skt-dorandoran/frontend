package org.duckdns.dorandoran.callaiassistant.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.duckdns.dorandoran.callaiassistant.ui.viewmodel.ConversationHistoryItem
import org.json.JSONArray
import org.json.JSONObject

data class AiSuggestedAnswer(
    val id: String,
    val text: String,
    val tone: String,
    val priority: String
)

data class AiSuggestionResponse(
    val callId: String,
    val answers: List<AiSuggestedAnswer>
)

object AiSuggestionApi {
    private const val TAG = "AiSuggestionApi"
    private const val BASE_URL = "https://dorandoran.dev"

    suspend fun generateResponse(
        callId: String,
        userSpeech: String,
        conversationHistory: List<ConversationHistoryItem>,
        phoneNumber: String
    ): AiSuggestionResponse? = withContext(Dispatchers.IO) {
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
                put("userSpeech", userSpeech)
                put("conversationHistory", historyJson)
                put("phoneNumber", phoneNumber)
            }
            val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$BASE_URL/api/v1/ai/generate-response")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val responseText = response.body?.string().orEmpty()
            val detail = extractDetail(responseText)

            if (!response.isSuccessful) {
                Log.e(
                    TAG,
                    "generate-response failed: statusCode=${response.code}, detail=$detail"
                )
                return@withContext null
            }
            Log.d(
                TAG,
                "generate-response success: statusCode=${response.code}, detail=$detail"
            )

            val rootJson = JSONObject(responseText)
            val responseJson = resolvePayloadJson(rootJson)
            val answersJson = extractAnswersArray(responseJson)
            val answers = buildList {
                for (i in 0 until answersJson.length()) {
                    val item = answersJson.optJSONObject(i) ?: continue
                    add(
                        AiSuggestedAnswer(
                            id = item.optString("id"),
                            text = item.optString("text"),
                            tone = item.optString("tone"),
                            priority = item.optString("priority")
                        )
                    )
                }
            }
            Log.d(TAG, "generate-response parsed answersCount=${answers.size}")
            AiSuggestionResponse(callId = responseJson.optString("callId", callId), answers = answers)
        } catch (e: Exception) {
            Log.e(TAG, "generate-response exception: ${e.message}", e)
            null
        }
    }

    private fun extractDetail(responseText: String): String {
        if (responseText.isBlank()) return "empty response body"
        return try {
            val json = JSONObject(responseText)
            json.optString("detail")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error") }
                .ifBlank { responseText.take(240) }
        } catch (_: Exception) {
            responseText.take(240)
        }
    }

    // Some backends wrap the actual payload JSON in "detail"/"data"/"result".
    private fun resolvePayloadJson(root: JSONObject): JSONObject {
        if (root.has("answers") || root.has("responses")) return root

        val nestedObjectKeys = listOf("detail", "data", "result", "response", "payload")
        nestedObjectKeys.forEach { key ->
            val nestedObj = root.optJSONObject(key)
            if (nestedObj != null && (nestedObj.has("answers") || nestedObj.has("responses"))) {
                return nestedObj
            }
        }

        nestedObjectKeys.forEach { key ->
            val nestedString = root.optString(key)
            if (nestedString.isBlank()) return@forEach
            try {
                val parsed = JSONObject(nestedString)
                if (parsed.has("answers") || parsed.has("responses")) {
                    return parsed
                }
            } catch (_: Exception) {
                // ignore and continue
            }
        }

        return root
    }

    private fun extractAnswersArray(payload: JSONObject): JSONArray {
        return payload.optJSONArray("answers")
            ?: payload.optJSONArray("responses")
            ?: payload.optJSONArray("suggestions")
            ?: JSONArray()
    }
}
