#include "tts_pcm_queue.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "TtsPcmQueue"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

TtsPcmQueue::TtsPcmQueue() {
    LOGD("TtsPcmQueue created");
}

TtsPcmQueue::~TtsPcmQueue() {
    LOGD("TtsPcmQueue destroyed");
}

void TtsPcmQueue::Push(const int16_t* samples, size_t count) {
    if (!samples || count == 0) return;
    
    std::lock_guard<std::mutex> lock(mutex_);
    for (size_t i = 0; i < count; i++) {
        queue_.push(samples[i]);
    }
    
    LOGD("Pushed %zu samples, total available: %zu", count, queue_.size());
}

size_t TtsPcmQueue::Pop(int16_t* output, size_t requested_count) {
    if (!output || requested_count == 0) return 0;
    
    std::lock_guard<std::mutex> lock(mutex_);
    
    size_t actual_count = std::min(requested_count, queue_.size());
    
    for (size_t i = 0; i < actual_count; i++) {
        output[i] = queue_.front();
        queue_.pop();
    }
    
    // 남은 샘플이 부족하면 0으로 채움
    if (actual_count < requested_count) {
        std::memset(output + actual_count, 0, 
                   (requested_count - actual_count) * sizeof(int16_t));
    }
    
    return actual_count;
}

size_t TtsPcmQueue::Available() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return queue_.size();
}

void TtsPcmQueue::Clear() {
    std::lock_guard<std::mutex> lock(mutex_);
    while (!queue_.empty()) {
        queue_.pop();
    }
    LOGD("Queue cleared");
}
