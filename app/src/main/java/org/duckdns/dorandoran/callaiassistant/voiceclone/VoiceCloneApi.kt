package org.duckdns.dorandoran.callaiassistant.voiceclone

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

object VoiceCloneApi {
    /**
     * 음성 클론 모델 생성 요청 (음성 샘플 업로드 후)
     * @param file 합쳐진 음성 샘플 파일
     * @return voiceId (null: 실패)
     */
    fun requestVoiceClone(file: File): String? {
        return try {
            val client = OkHttpClient()
            val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaTypeOrNull()))
                .build()
            val request = Request.Builder()
                .url("https://dorandoran.dev/api/ai/voice-clone") // 실제 엔드포인트로 교체 필요
                .post(requestBody)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                val json = JSONObject(body)
                json.optString("voiceId", null)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 음성 클론 모델 삭제 요청
     * @param voiceId 삭제할 voiceId
     * @return 성공 여부
     */
    fun deleteVoiceClone(voiceId: String): Boolean {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://dorandoran.dev/api/ai/voice-clone/$voiceId") // 실제 엔드포인트로 교체 필요
                .delete()
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
