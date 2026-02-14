#include <jni.h>
#include <android/log.h>
#include <memory>
#include <cstring>
#include "audio_mixer.h"
#include "tts_pcm_queue.h"

#define LOG_TAG "AudioMixerJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 네이티브 핸들 구조체
struct NativeHandle {
    std::unique_ptr<AudioMixer> mixer;
    std::unique_ptr<TtsPcmQueue> tts_queue;
};

extern "C" {

// 네이티브 핸들 생성
JNIEXPORT jlong JNICALL
Java_org_duckdns_dorandoran_callaiassistant_webrtc_CustomAudioDeviceModule_nativeCreate(
        JNIEnv* env, jobject thiz) {
    LOGD("nativeCreate called");
    
    auto* handle = new NativeHandle();
    handle->mixer = std::make_unique<AudioMixer>();
    handle->tts_queue = std::make_unique<TtsPcmQueue>();
    
    LOGD("Native handle created: %p", handle);
    return reinterpret_cast<jlong>(handle);
}

// 네이티브 핸들 해제
JNIEXPORT void JNICALL
Java_org_duckdns_dorandoran_callaiassistant_webrtc_CustomAudioDeviceModule_nativeDestroy(
        JNIEnv* env, jobject thiz, jlong native_handle) {
    LOGD("nativeDestroy called");
    
    if (native_handle == 0) {
        LOGE("Invalid native handle");
        return;
    }
    
    auto* handle = reinterpret_cast<NativeHandle*>(native_handle);
    delete handle;
    
    LOGD("Native handle destroyed");
}

// TTS PCM 데이터를 큐에 추가
JNIEXPORT void JNICALL
Java_org_duckdns_dorandoran_callaiassistant_webrtc_CustomAudioDeviceModule_nativePushTts(
        JNIEnv* env, jobject thiz, jlong native_handle,
        jshortArray samples) {
    
    if (native_handle == 0) {
        LOGE("Invalid native handle");
        return;
    }
    
    if (samples == nullptr) {
        LOGE("Samples array is null");
        return;
    }
    
    auto* handle = reinterpret_cast<NativeHandle*>(native_handle);
    
    jshort* pcm = env->GetShortArrayElements(samples, nullptr);
    jsize len = env->GetArrayLength(samples);
    
    if (pcm == nullptr) {
        LOGE("Failed to get array elements");
        return;
    }
    
    handle->tts_queue->Push(pcm, len);
    
    env->ReleaseShortArrayElements(samples, pcm, JNI_ABORT);
}

// 마이크 샘플과 TTS를 믹싱
JNIEXPORT void JNICALL
Java_org_duckdns_dorandoran_callaiassistant_webrtc_CustomAudioDeviceModule_nativeMix(
        JNIEnv* env, jobject thiz, jlong native_handle,
        jshortArray mic_samples) {
    
    if (native_handle == 0) {
        LOGE("Invalid native handle");
        return;
    }
    
    if (mic_samples == nullptr) {
        LOGE("Mic samples array is null");
        return;
    }
    
    auto* handle = reinterpret_cast<NativeHandle*>(native_handle);
    
    jshort* mic_pcm = env->GetShortArrayElements(mic_samples, nullptr);
    jsize len = env->GetArrayLength(mic_samples);
    
    if (mic_pcm == nullptr) {
        LOGE("Failed to get mic array elements");
        return;
    }
    
    // TTS 큐가 비어있으면 믹싱하지 않음
    if (handle->tts_queue->Available() == 0) {
        env->ReleaseShortArrayElements(mic_samples, mic_pcm, JNI_ABORT);
        return;
    }
    
    // TTS 샘플 읽기
    std::vector<int16_t> tts_buffer(len);
    size_t read_count = handle->tts_queue->Pop(tts_buffer.data(), len);
    
    // 믹싱 (mic_pcm이 in-place로 수정됨)
    handle->mixer->Mix(mic_pcm, tts_buffer.data(), len);
    
    // 수정된 데이터를 Java 배열에 반영
    env->ReleaseShortArrayElements(mic_samples, mic_pcm, 0);
    
    LOGD("Mixed %d samples, TTS available: %zu", len, handle->tts_queue->Available());
}

// 큐에 남은 TTS 샘플 수
JNIEXPORT jint JNICALL
Java_org_duckdns_dorandoran_callaiassistant_webrtc_CustomAudioDeviceModule_nativeGetAvailable(
        JNIEnv* env, jobject thiz, jlong native_handle) {
    
    if (native_handle == 0) {
        return 0;
    }
    
    auto* handle = reinterpret_cast<NativeHandle*>(native_handle);
    return static_cast<jint>(handle->tts_queue->Available());
}

// 큐 비우기
JNIEXPORT void JNICALL
Java_org_duckdns_dorandoran_callaiassistant_webrtc_CustomAudioDeviceModule_nativeClear(
        JNIEnv* env, jobject thiz, jlong native_handle) {
    
    if (native_handle == 0) {
        return;
    }
    
    auto* handle = reinterpret_cast<NativeHandle*>(native_handle);
    handle->tts_queue->Clear();
    
    LOGD("Queue cleared");
}

} // extern "C"
