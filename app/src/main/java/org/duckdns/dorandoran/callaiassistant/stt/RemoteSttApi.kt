package org.duckdns.dorandoran.callaiassistant.stt

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object RemoteSttApi {
    private const val TAG = "RemoteSttApi"
    private const val WHISPER_X_BASE_URL = "http://10.0.234.18:8000"

    /**
     * WhisperX STT API 호출 (wav 파일 업로드)
     * @param file 음성 파일
     * @return 인식된 텍스트 (null: 실패)
     */
    suspend fun recognize(file: File): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "recognize request start: url=$WHISPER_X_BASE_URL/transcribe, file=${file.absolutePath}, size=${file.length()}")
            val client = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .writeTimeout(6, TimeUnit.SECONDS)
                .build()
            val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaTypeOrNull()))
                .build()
            val request = Request.Builder()
                .url("$WHISPER_X_BASE_URL/transcribe")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                Log.d(
                    TAG,
                    "recognize response: status=${response.code}, successful=${response.isSuccessful}, body=$responseBody"
                )
                if (!response.isSuccessful) {
                    Log.w(TAG, "recognize failed: status=${response.code}")
                    return@withContext null
                }
                if (responseBody.isBlank()) return@withContext null
                val json = JSONObject(responseBody)
                json.optString("text").trim().ifBlank { null }
            }
        } catch (e: Exception) {
            Log.w(TAG, "recognize exception: ${e.message}")
            null
        }
    }
}
