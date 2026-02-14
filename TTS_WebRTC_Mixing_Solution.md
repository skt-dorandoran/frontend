# WebRTC TTS 네이티브 믹싱 문제 해결 기록

## 목표
- TTS PCM 데이터를 WebRTC 오디오 스트림에 네이티브로 믹싱하여 상대방이 실제로 TTS 음성을 듣게 하는 것

## 주요 문제 및 해결 과정

### 1. WebRTC 커스텀 빌드 및 AAR 생성
- Docker 환경에서 WebRTC 소스 체크아웃 및 빌드
- 커스텀 AudioDeviceModule 구현 (C++/JNI/Java)
- TTS PCM 큐 및 믹싱 로직 추가
- webrtc-custom.aar 생성 및 app/libs로 복사

### 2. Gradle 통합 및 빌드 오류
- build.gradle.kts에서 webrtc-custom.aar 의존성 추가
- settings.gradle.kts에 flatDir { dirs("app/libs") } 설정
- files(), mapOf(), implementation("webrtc-custom") 등 다양한 의존성 선언 방식 실험
- JVM 17+ 환경 요구, macOS에서 openjdk@17 설치 및 환경변수 설정

### 3. .aar 인식 및 네이티브 믹싱 연결
- webrtc-custom.aar 내부 클래스 구조 분석 (jar tf/unzip/javap 활용)
- org.webrtc.audio.TtsAudioInjector 클래스와 nativePushPcm, nativeClear, nativeGetAvailable 메서드 확인
- CustomAudioDeviceModule에서 TtsAudioInjector 네이티브 메서드 직접 호출하도록 코드 복구

### 4. 빌드 성공 및 실제 TTS 믹싱 테스트
- 빌드 성공 후 실제 통화에서 TTS PCM 데이터가 nativePushPcm()을 통해 WebRTC 스트림에 주입됨을 확인
- 상대방이 TTS 음성을 정상적으로 듣는지 테스트 완료

## 주요 자동화/분석 도구
- jar, unzip, javap 등으로 AAR 내부 구조 분석
- Gradle 빌드 자동화 및 오류 반복 수정
- 코드 자동 패치 및 주석 처리/복구

## 결론
- webrtc-custom.aar와 CustomAudioDeviceModule, TtsAudioInjector 네이티브 메서드 연결로 TTS PCM 믹싱이 성공적으로 구현됨
- 모든 빌드/통합/테스트 과정 자동화 및 반복 오류 수정
- 실제 통화에서 TTS 음성이 상대방에게 들리는 것까지 검증 완료

---

이 기록은 향후 WebRTC 커스텀 오디오 믹싱 및 TTS 네이티브 통합 작업에 참고할 수 있습니다.
