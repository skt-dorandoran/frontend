#include "audio_mixer.h"
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "AudioMixer"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

AudioMixer::AudioMixer() {
    LOGD("AudioMixer created");
}

AudioMixer::~AudioMixer() {
    LOGD("AudioMixer destroyed");
}

int16_t AudioMixer::ClipSample(int32_t sample) {
    if (sample > 32767) return 32767;
    if (sample < -32768) return -32768;
    return static_cast<int16_t>(sample);
}

void AudioMixer::Mix(int16_t* mic_samples, 
                     const int16_t* tts_samples,
                     size_t sample_count) {
    if (!mic_samples || !tts_samples || sample_count == 0) {
        return;
    }

    for (size_t i = 0; i < sample_count; i++) {
        // 마이크 0.7배 + TTS 0.5배로 믹싱
        int32_t mic_adjusted = static_cast<int32_t>(mic_samples[i] * 0.7f);
        int32_t tts_adjusted = static_cast<int32_t>(tts_samples[i] * 0.5f);
        int32_t mixed = mic_adjusted + tts_adjusted;
        
        mic_samples[i] = ClipSample(mixed);
    }
    
    LOGD("Mixed %zu samples", sample_count);
}
