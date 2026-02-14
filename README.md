# T.mate (SKT FLY AI 8기 도란도란 1팀, 통화 AI Agent)

## 소개
이 프로젝트는 청각장애인을 위한 AI 음성 통화 에이전트 기능을 제공하는 Android 앱 프론트엔드이며, 서비스명은 **T.mate**입니다. WebRTC 기반 실시간 통화에 TTS(음성합성) 데이터를 네이티브로 믹싱하여, 상대방이 실제로 TTS 음성을 들을 수 있도록 구현되었습니다.

## 주요 기능
- WebRTC 기반 실시간 음성 통화
- TTS(텍스트-음성 변환) PCM 데이터 실시간 믹싱 및 송출
- 커스텀 AudioDeviceModule 및 JNI 연동
- Compose 기반 UI 및 Android 최신 아키텍처 적용
- 다양한 통화/오디오 제어 기능

## 시스템 구조
- Kotlin(Android) + Jetpack Compose UI
- JNI(C++)로 WebRTC 네이티브 오디오 믹싱
- 커스텀 빌드된 webrtc-custom.aar, sherpa-onnx.aar 활용
- 주요 네이티브/자바 구조:
	- app/src/main/cpp: 오디오 믹서, TTS PCM 큐, JNI 브릿지
	- app/libs: 커스텀 AAR 라이브러리
	- app/src/main/java: 앱 로직 및 UI

## 빌드 및 실행 방법
1. Android SDK, NDK, CMake(3.22.1) 설치 필요 (webrtc-build/android-sdk/ 경로 사용)
2. webrtc-custom.aar, sherpa-onnx.aar을 app/libs에 위치시킴
3. local.properties에 sdk.dir=webrtc-build/android-sdk 경로 지정
4. Gradle 빌드:
	 ```sh
	 ./gradlew assembleDebug
	 ```
5. Android 13(API 33) 이상 에뮬레이터/단말에서 실행 권장

## 주요 버전 정보
- Android Gradle Plugin: 최신 (settings.gradle.kts 참고)
- compileSdk: 35, minSdk: 35, targetSdk: 36
- Java: 17
- CMake: 3.22.1

## 기술 스택
- Kotlin, Jetpack Compose, Android SDK/NDK
- WebRTC (커스텀 빌드)
- JNI, C++
- Gradle, CMake

## 문서
- [TTS WebRTC 네이티브 믹싱 문제 해결 기록](TTS_WebRTC_Mixing_Solution.md)
- [왜 커스텀 WebRTC에서만 TTS 믹싱이 동작하는가?](TTS_WebRTC_Mixing_Why_Custom_Works.md)
