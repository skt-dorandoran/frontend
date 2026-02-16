package org.duckdns.dorandoran.callaiassistant.voiceclone

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

object VoiceCloneApi {
    private const val TAG = "VoiceCloneApi"
    private const val BASE_URL = "https://dorandoran.dev"

    /**
     * AI 음성 클론 모델 학습 요청
     * @return voiceId 등 (성공 시 JSONObject), 실패 시 null
     */
    fun trainVoiceClone(modelName: String, modelFile: File): JSONObject? {
        return try {
            val client = OkHttpClient()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("modelName", modelName)
                .addFormDataPart(
                    "modelFile",
                    modelFile.name,
                    modelFile.asRequestBody("audio/x-m4a".toMediaTypeOrNull())
                )
                .build()
            val request = Request.Builder()
                .url("$BASE_URL/api/v1/voice/clone")
                .post(requestBody)
                .build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string()
            if (response.isSuccessful) {
                android.util.Log.d(TAG, "trainVoiceClone 성공: $respBody")
                JSONObject(respBody)
            } else {
                android.util.Log.e(TAG, "trainVoiceClone 실패: status=${response.code}, body=$respBody, modelName=$modelName, modelFile=${modelFile.absolutePath}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "trainVoiceClone 예외: ${e.message}", e)
            null
        }
    }

    /**
     * AI 음성 클론 모델 삭제
     * @return 성공 시 true, 실패 시 false
     */
    fun deleteVoiceClone(voiceId: String): Boolean {
        return try {
            val client = OkHttpClient()
            val json = JSONObject()
            json.put("voiceId", voiceId)
            val body = okhttp3.RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
            val request = Request.Builder()
                .url("$BASE_URL/api/v1/voice/$voiceId")
                .delete(body)
                .build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string()
            if (response.isSuccessful) {
                val respJson = JSONObject(respBody)
                val result = respJson.optString("status") == "ok"
                android.util.Log.d(TAG, "deleteVoiceClone 성공: $respBody")
                result
            } else {
                android.util.Log.e(TAG, "deleteVoiceClone 실패: status=${response.code}, body=$respBody, voiceId=$voiceId")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "deleteVoiceClone 예외: ${e.message}", e)
            false
        }
    }
}
