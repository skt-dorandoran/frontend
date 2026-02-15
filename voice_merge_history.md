# 음성 병합(Voice Merge) 작업 기록

## 1. 요구사항 및 목표
- Android에서 여러 m4a(음성) 파일을 하나로 병합하고, 병합된 파일을 재생하는 기능 구현
- 외부 ffmpeg 라이브러리 대신 표준 Android API(MediaExtractor, MediaMuxer)만 사용

## 2. 주요 작업 단계 및 과정

### 2.1. ffmpeg-kit 기반 병합 로직 제거
- 기존에는 ffmpeg-kit을 이용해 m4a 파일을 병합
- ffmpeg-kit 의존성 문제(빌드/런타임 오류, aar/so 파일 문제)로 인해 표준 API로 대체 필요
- build.gradle.kts에서 ffmpeg-kit aar 의존성 완전 제거

### 2.2. media3-transformer 시도 및 한계
- media3-transformer로 병합 시도했으나, 여러 m4a를 하나로 직접 병합하는 기능은 미지원
- 단일 파일 변환에는 적합하지만, 여러 파일 병합에는 부적합

### 2.3. MediaExtractor + MediaMuxer 기반 병합 로직 구현
- Android 표준 API(MediaExtractor, MediaMuxer)로 여러 m4a 파일을 하나로 합치는 유틸리티 함수 작성
- 병합 로직을 AudioMergeUtil 오브젝트로 분리하여 재사용성 및 유지보수성 향상
- VoiceTrainingActivity에서는 AudioMergeUtil.mergeM4aFiles만 호출하도록 구조 단순화

### 2.4. 병합 시 발생한 주요 오류 및 해결 과정

#### (1) out-of-order frame 오류
- 로그: `do not support out of order frames (timestamp: 0 < last: ...)`
- 원인: 두 번째 이후 파일의 첫 sampleTime이 0이거나, 이전 파일보다 작은 값으로 기록되어 발생
- 해결: 각 파일의 첫 sampleTime을 0으로 맞추고, 마지막으로 기록한 presentationTimeUs를 다음 파일의 offset으로 누적 관리하도록 병합 로직 수정

#### (2) 병합 후 재생 불가
- 원인: presentationTimeUs 누적이 잘못되어, 병합된 m4a가 손상됨
- 해결: 마지막으로 기록한 presentationTimeUs + 1을 다음 파일의 offset으로 사용하여, 모든 프레임이 시간 순서대로 이어지도록 보장

## 3. 최종 구조 및 결과
- AudioMergeUtil.kt: MediaExtractor + MediaMuxer로 m4a 병합, out-of-order 오류 방지 로직 포함
- VoiceTrainingActivity.kt: 병합/재생 로직이 util 호출로 단순화, UI/UX 정상 동작
- 표준 Android API만 사용하므로 빌드/런타임 호환성 우수
- 실제 기기에서 여러 m4a 파일을 정상적으로 하나로 이어붙이고, 재생까지 성공

## 4. 결론 및 참고사항
- ffmpeg 등 외부 네이티브 라이브러리 없이, Android 표준 API만으로도 m4a 병합이 가능함
- presentationTimeUs 누적 관리가 핵심(오류 방지)
- 병합 유틸리티 분리로 코드 유지보수성 향상
- 향후 wav 등 다른 포맷도 유사 방식으로 확장 가능
