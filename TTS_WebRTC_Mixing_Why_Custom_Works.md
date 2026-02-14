# WebRTC TTS 믹싱이 커스텀에서 동작하는 원리

## 일반 WebRTC(Android)에서 TTS 믹싱이 안 되는 이유
- 기본 WebRTC 라이브러리는 마이크 입력만 오디오 스트림에 전달
- TTS PCM 데이터를 직접 WebRTC 오디오 스트림에 주입할 수 있는 API가 없음
- AudioDeviceModule, JavaAudioDeviceModule 등에서 마이크만 처리
- 네이티브(C++) 레이어에서 믹싱 로직이 없으므로 TTS 음성이 상대방에게 전달되지 않음

## 커스텀 WebRTC에서 동작하는 핵심 변경점
1. **AudioDeviceModule 커스텀 구현**
   - 기존 JavaAudioDeviceModule 대신 C++/JNI/Java를 연결하는 CustomAudioDeviceModule 구현
   - 마이크 샘플과 TTS PCM 데이터를 네이티브 큐에서 합쳐서 믹싱

2. **TTS PCM 큐 및 네이티브 믹싱 로직 추가**
   - org.webrtc.audio.TtsAudioInjector 클래스 생성 (JNI 네이티브 메서드: nativePushPcm, nativeClear, nativeGetAvailable)
   - injectTtsPcm()에서 TTS PCM 데이터를 ShortArray로 변환 후 nativePushPcm()으로 전달
   - C++ 레이어에서 마이크 샘플과 TTS 큐를 실시간으로 합쳐서 WebRTC 오디오 스트림에 송출

3. **AAR 빌드 및 통합**
   - 커스텀 C++/JNI/Java 코드를 포함한 webrtc-custom.aar 빌드
   - app/libs에 복사 후 Gradle 의존성으로 연결
   - CustomAudioDeviceModule에서 TtsAudioInjector 네이티브 메서드 호출

## 실제 동작 흐름
1. 앱에서 TTS PCM 데이터 생성 (예: SherpaOnnxTtsManager)
2. CustomAudioDeviceModule.injectTtsPcm() 호출 → TtsAudioInjector.nativePushPcm()로 네이티브 큐에 저장
3. 네이티브(C++)에서 마이크 샘플과 TTS 큐를 합쳐서 WebRTC 오디오 스트림에 믹싱
4. 상대방이 통화에서 TTS 음성을 실제로 듣게 됨

## 결론
- 커스텀 WebRTC는 네이티브 레이어에서 마이크와 TTS PCM을 실시간으로 합쳐서 송출하는 믹싱 로직이 추가됨
- 기존 WebRTC는 마이크만 송출, 커스텀은 TTS까지 믹싱 가능
- 핵심은 AudioDeviceModule 커스텀 구현 + JNI 네이티브 믹싱 + AAR 통합

---

이 기록은 커스텀 WebRTC에서 TTS 믹싱이 실제로 동작하는 원리와 핵심 변경점을 설명합니다.
