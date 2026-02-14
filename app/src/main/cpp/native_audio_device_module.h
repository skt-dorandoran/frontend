#ifndef NATIVE_AUDIO_DEVICE_MODULE_H
#define NATIVE_AUDIO_DEVICE_MODULE_H

#include <jni.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <memory>
#include <atomic>
#include <thread>
#include "audio_mixer.h"
#include "tts_pcm_queue.h"

// WebRTC AudioDeviceModule 최소 구현
class NativeAudioDeviceModule {
public:
    NativeAudioDeviceModule();
    ~NativeAudioDeviceModule();

    // 초기화 및 제어
    bool Init();
    void Terminate();
    bool StartRecording();
    bool StopRecording();
    bool IsRecording() const { return recording_.load(); }

    // TTS 주입
    void PushTtsPcm(const int16_t* samples, size_t count);
    size_t GetTtsQueueSize() const;
    void ClearTtsQueue();

    // AudioTransport 콜백 설정 (WebRTC에서 호출)
    void SetAudioTransport(void* transport);

private:
    // OpenSL ES 초기화/정리
    bool InitOpenSLES();
    void DestroyOpenSLES();
    
    // 녹음 스레드
    void RecordingThread();
    
    // OpenSL ES 객체들
    SLObjectItf engine_object_ = nullptr;
    SLEngineItf engine_ = nullptr;
    SLObjectItf recorder_object_ = nullptr;
    SLRecordItf recorder_ = nullptr;
    SLAndroidSimpleBufferQueueItf buffer_queue_ = nullptr;
    
    // 오디오 버퍼
    static constexpr size_t kSampleRate = 8000;
    static constexpr size_t kChannels = 1;
    static constexpr size_t kFrameSizeMs = 10;
    static constexpr size_t kFrameSizeSamples = kSampleRate * kFrameSizeMs / 1000;
    static constexpr size_t kBufferCount = 2;
    
    int16_t recording_buffers_[kBufferCount][kFrameSizeSamples];
    size_t current_buffer_index_ = 0;
    
    // 믹서 및 큐
    std::unique_ptr<AudioMixer> mixer_;
    std::unique_ptr<TtsPcmQueue> tts_queue_;
    
    // 상태
    std::atomic<bool> initialized_{false};
    std::atomic<bool> recording_{false};
    std::thread recording_thread_;
    
    // AudioTransport (WebRTC 콜백 인터페이스)
    void* audio_transport_ = nullptr;
    
    // OpenSL ES 콜백
    static void BufferQueueCallback(SLAndroidSimpleBufferQueueItf bq, void* context);
    void ProcessRecordedData(const int16_t* audio_data, size_t samples);
};

#endif // NATIVE_AUDIO_DEVICE_MODULE_H
