package org.duckdns.dorandoran.callaiassistant.stt

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

object RemoteSttApi {
    /**
     * 서버 STT API 호출 (wav 파일 업로드)
     * @param file 음성 파일
     * @return 인식된 텍스트 (null: 실패)
     */
    fun recognize(file: File): String? {
        return try {
            val client = OkHttpClient()
            val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaTypeOrNull()))
                .build()
            val request = Request.Builder()
                .url("https://dorandoran.dev/api/stt/recognize") // 실제 엔드포인트로 교체 필요
                .post(requestBody)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                val json = JSONObject(body)
                json.optString("text", null)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
