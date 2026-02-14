#ifndef AUDIO_MIXER_H
#define AUDIO_MIXER_H

#include <cstdint>
#include <cstddef>

class AudioMixer {
public:
    AudioMixer();
    ~AudioMixer();

    // 마이크 PCM과 TTS PCM을 믹싱
    // mic_samples: 마이크 샘플 (in-place 수정됨)
    // tts_samples: TTS 샘플
    // sample_count: 샘플 개수
    void Mix(int16_t* mic_samples, 
             const int16_t* tts_samples,
             size_t sample_count);

private:
    // Soft clipping을 적용한 믹싱
    int16_t ClipSample(int32_t sample);
};

#endif // AUDIO_MIXER_H
