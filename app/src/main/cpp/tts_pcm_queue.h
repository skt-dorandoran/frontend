#ifndef TTS_PCM_QUEUE_H
#define TTS_PCM_QUEUE_H

#include <cstdint>
#include <queue>
#include <mutex>
#include <vector>

class TtsPcmQueue {
public:
    TtsPcmQueue();
    ~TtsPcmQueue();

    // TTS PCM 데이터를 큐에 추가
    void Push(const int16_t* samples, size_t count);
    
    // 큐에서 요청된 샘플 수만큼 읽기
    // output: 출력 버퍼
    // requested_count: 요청 샘플 수
    // 반환: 실제 읽은 샘플 수
    size_t Pop(int16_t* output, size_t requested_count);
    
    // 큐에 남은 샘플 수
    size_t Available() const;
    
    // 큐 비우기
    void Clear();

private:
    std::queue<int16_t> queue_;
    mutable std::mutex mutex_;
};

#endif // TTS_PCM_QUEUE_H
