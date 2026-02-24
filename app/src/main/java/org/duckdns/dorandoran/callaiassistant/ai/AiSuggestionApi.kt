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
import okio.Buffer

data class AiSuggestedAnswer(
    val id: String,
    val text: String,
    val tone: String,
    val priority: Int
)

data class AiSuggestionResponse(
    val callId: String,
    val responses: List<AiSuggestedAnswer>
)

object AiSuggestionApi {
    private const val TAG = "AiSuggestionApi"

    suspend fun generateResponse(
        callId: String,
        userSpeech: String,
        conversationHistory: List<ConversationHistoryItem>,
        onPartialResponses: ((String?, String?) -> Unit)? = null
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
            }
            val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("${NetworkUrlUtil.DORANDORAN_HTTPS_BASE_URL}/api/v1/ai/generate-response")
                .post(body)
                .build()
            val streamed = StringBuilder()
            var responsesJson: JSONArray? = null
            var streamedCallId: String? = null
            var lastPartialTop1: String? = null
            var lastPartialTop2: String? = null

            client.newCall(request).execute().use { response ->
                val responseBody = response.body
                if (responseBody == null) {
                    Log.e(TAG, "generate-response failed: empty response body")
                    return@withContext null
                }

                if (!response.isSuccessful) {
                    val responseText = responseBody.string()
                    val detail = extractDetail(responseText)
                    Log.e(
                        TAG,
                        "generate-response failed: statusCode=${response.code}, detail=$detail"
                    )
                    return@withContext null
                }

                val source = responseBody.source()
                val chunkBuffer = Buffer()

                while (true) {
                    val read = source.read(chunkBuffer, 1024L)
                    if (read == -1L) break
                    val chunk = chunkBuffer.readUtf8()
                    if (chunk.isEmpty()) continue
                    streamed.append(chunk)

                    if (streamedCallId.isNullOrBlank()) {
                        streamedCallId = extractStreamCallId(streamed.toString())
                    }
                    if (responsesJson == null) {
                        responsesJson = extractResponsesArrayFromStream(streamed.toString())
                    }

                    val (partialTop1, partialTop2) = extractPartialResponseTexts(streamed.toString())
                    if (partialTop1 != lastPartialTop1 || partialTop2 != lastPartialTop2) {
                        lastPartialTop1 = partialTop1
                        lastPartialTop2 = partialTop2
                        onPartialResponses?.invoke(partialTop1, partialTop2)
                    }

                    // responses 배열이 완성되면 suffix(generatedAt/processingTime) 대기 없이 즉시 반환
                    if (responsesJson != null) break
                }

                if (responsesJson == null) {
                    val responseText = streamed.toString()
                    Log.d(
                        TAG,
                        "generate-response stream fallback parse: statusCode=${response.code}"
                    )
                    val rootJson = JSONObject(responseText)
                    val responseJson = resolvePayloadJson(rootJson)
                    responsesJson = extractResponsesArray(responseJson)
                    if (streamedCallId.isNullOrBlank()) {
                        streamedCallId = responseJson.optString("callId", callId)
                    }
                } else {
                    Log.d(
                        TAG,
                        "generate-response stream parsed incrementally: statusCode=${response.code}"
                    )
                }
            }

            val finalizedResponsesJson = responsesJson ?: JSONArray()

            val responses = buildList {
                for (i in 0 until finalizedResponsesJson.length()) {
                    val item = finalizedResponsesJson.optJSONObject(i) ?: continue
                    add(
                        AiSuggestedAnswer(
                            id = item.optString("id"),
                            text = item.optString("text"),
                            tone = item.optString("tone"),
                            priority = item.optInt("priority", i + 1)
                        )
                    )
                }
            }
            Log.d(TAG, "generate-response parsed responsesCount=${responses.size}")
            AiSuggestionResponse(
                callId = streamedCallId ?: callId,
                responses = responses
            )
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

    private fun extractResponsesArray(payload: JSONObject): JSONArray {
        return payload.optJSONArray("responses")
            ?: payload.optJSONArray("answers")
            ?: JSONArray()
    }

    private fun extractStreamCallId(streamed: String): String? {
        val key = "\"callId\""
        val keyIndex = streamed.indexOf(key)
        if (keyIndex < 0) return null
        val colonIndex = streamed.indexOf(':', keyIndex + key.length)
        if (colonIndex < 0) return null
        val firstQuote = streamed.indexOf('"', colonIndex + 1)
        if (firstQuote < 0) return null
        val secondQuote = streamed.indexOf('"', firstQuote + 1)
        if (secondQuote < 0) return null
        return streamed.substring(firstQuote + 1, secondQuote)
    }

    private fun extractResponsesArrayFromStream(streamed: String): JSONArray? {
        val responsesKeyIndex = streamed.indexOf("\"responses\"")
        if (responsesKeyIndex < 0) return null

        val arrayStartIndex = streamed.indexOf('[', responsesKeyIndex)
        if (arrayStartIndex < 0) return null

        var inString = false
        var escaped = false
        var depth = 0
        var arrayEndIndex = -1

        for (i in arrayStartIndex until streamed.length) {
            val ch = streamed[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '[' -> depth += 1
                ']' -> {
                    depth -= 1
                    if (depth == 0) {
                        arrayEndIndex = i
                        break
                    }
                }
            }
        }

        if (arrayEndIndex < 0) return null

        val arrayJson = streamed.substring(arrayStartIndex, arrayEndIndex + 1)
        return try {
            JSONArray(arrayJson)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractPartialResponseTexts(streamed: String): Pair<String?, String?> {
        val responsesKeyIndex = streamed.indexOf("\"responses\"")
        if (responsesKeyIndex < 0) return null to null

        val arrayStartIndex = streamed.indexOf('[', responsesKeyIndex)
        if (arrayStartIndex < 0) return null to null

        val texts = mutableListOf<String>()
        var searchIndex = arrayStartIndex

        while (texts.size < 2) {
            val keyIndex = streamed.indexOf("\"text\"", searchIndex)
            if (keyIndex < 0) break

            val colonIndex = streamed.indexOf(':', keyIndex + 6)
            if (colonIndex < 0) break

            val valueStartQuote = streamed.indexOf('"', colonIndex + 1)
            if (valueStartQuote < 0) break

            val sb = StringBuilder()
            var i = valueStartQuote + 1
            var escaped = false
            var closed = false

            while (i < streamed.length) {
                val ch = streamed[i]
                if (escaped) {
                    sb.append(ch)
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    closed = true
                    break
                } else {
                    sb.append(ch)
                }
                i += 1
            }

            val parsed = unescapeJsonString(sb.toString()).trim()
            if (parsed.isNotEmpty()) {
                texts.add(parsed)
            }

            if (!closed) break
            searchIndex = i + 1
        }

        val top1 = texts.getOrNull(0)
        val top2 = texts.getOrNull(1)
        return top1 to top2
    }

    private fun unescapeJsonString(raw: String): String {
        return raw
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\/", "/")
    }
}
