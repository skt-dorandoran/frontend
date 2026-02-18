# Custom WebRTC Android SDK 사용 가이드

본 SDK는 표준 WebRTC에 **TTS 주입(Injection)** 및 **오디오 가로채기(STT용)** 기능이 추가된 커스텀 빌드 버전입니다.

## 1. 주요 기능
- **Remote Audio Interception**: 상대방의 목소리를 PCM 데이터로 추출하여 STT에 전달.
- **Local Audio Interception**: 내가 말하는 목소리(순수 마이크 입력)만 추출하여 STT에 전달.
- **TTS Injection**: 내가 생성한 TTS PCM 데이터를 마이크 스트림에 주입하여 상대방에게 전달 (내 목소리와 합성됨).

---

## 2. 상세 구현 방법

### 오디오 데이터 흐름 (Data Flow)
1. **Mic Input** -> **WebRtcAudioRecord** 캡처
2. **Local STT Sink**로 복사본 전달 (TTS 섞이기 전 순수 음성)
3. **TtsAudioInjector**에서 대기 중인 TTS PCM과 믹싱
4. **WebRTC Native Engine**으로 전달 -> **상대방에게 전송**

이 구조 덕분에 "내 STT 엔진"은 내가 말하는 것만 인식하고, "상대방"은 내 목소리와 TTS가 합쳐진 소리를 듣게 됩니다.

---

## 3. 상세 구현 방법 (계속)
상대방의 `AudioTrack`에 `AudioSink`를 추가하여 PCM 데이터를 실시간으로 받습니다.

```java
AudioTrack remoteAudioTrack = ...; // RtcReceiver로부터 획득
WebRtcAudioSink remoteSttSink = new WebRtcAudioSink((buffer, sampleRate, channels, frames) -> {
    // 여기서 buffer(PCM 16bit)를 STT 엔진으로 전달
    sttEngine.pushAudio(buffer);
});

remoteAudioTrack.addSink(remoteSttSink);
```

### 2.2 내 목소리 STT (Local Audio Sink)
`LocalAudioTrack`에도 동일하게 Sink를 부착합니다. 본 커스텀 SDK는 **TTS가 믹싱되기 전의 순수 마이크 데이터**를 보장합니다.

```java
AudioTrack localAudioTrack = ...; // LocalMediaStream에서 획득
WebRtcAudioSink localSttSink = new WebRtcAudioSink((buffer, sampleRate, channels, frames) -> {
    // 내가 말하는 음성만 STT 처리 (주입된 TTS는 포함되지 않음)
    localSttEngine.pushAudio(buffer);
});

localAudioTrack.addSink(localSttSink);
```

### 2.3 TTS 데이터 주입 (TTS Audio Injector)
`TtsAudioInjector`를 통해 PCM 데이터를 주입하면, WebRTC가 마이크 데이터를 처리할 때 자동으로 믹싱하여 상대방에게 전송합니다.

```java
// SDK 초기화 시 Injector 획득
TtsAudioInjector ttsInjector = CustomWebRtcContext.getTtsInjector();

// TTS 엔진에서 생성된 PCM 데이터(byte[])를 실시간 주입
void onTtsGenerated(byte[] pcmData) {
    ttsInjector.injectPcm(pcmData);
}
```

---

## 3. 오디오 포맷 규격
- **Type**: Linear PCM 16-bit
- **Sampling Rate**: 48,000Hz (권장)
- **Channels**: Mono (1ch)

## 4. 주의사항
- `TtsAudioInjector`에 너무 큰 버퍼를 한꺼번에 넣으면 오디오 지연(Latency)이 발생할 수 있으므로, 20ms 단위로 쪼개서 넣는 것을 권장합니다.
- `AudioSink`에서 받은 `ByteBuffer`는 다음 콜백 전까지만 유효하므로, 비동기 처리 시 반드시 데이터를 복사(clone)해서 사용하십시오.
