package org.duckdns.dorandoran.callaiassistant.voiceclone

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object VoiceCloneTtsApi {
    private const val TAG = "VoiceCloneTtsApi"
    private const val BASE_URL = "https://dorandoran.dev"

    /**
     * 음성 클론 TTS API 호출 및 WAV 파일 저장 후 경로 반환
     */
    suspend fun synthesizeVoiceClone(
        callId: String,
        text: String,
        voiceId: String,
        sourceType: String = "ai_response",
        context: Context
    ): File? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val json = JSONObject().apply {
                put("callId", callId)
                put("text", text)
                put("voiceId", voiceId)
                put("sourceType", sourceType)
            }
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$BASE_URL/api/v1/ai/synthesize-response")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            Log.d(TAG, "VoiceClone TTS API 응답: ${response.code} ${response.message}")
            if (!response.isSuccessful) {
                Log.e(TAG, "VoiceClone TTS API 실패: ${response.code} ${response.message}")
                return@withContext null
            }
            val wavBytes = response.body?.bytes() ?: return@withContext null
            val file = File.createTempFile("voice_clone_tts_", ".wav", context.cacheDir)
            FileOutputStream(file).use { it.write(wavBytes) }
            Log.d(TAG, "VoiceClone TTS 저장: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "VoiceClone TTS API 예외: ${e.message}", e)
            null
        }
    }
}
