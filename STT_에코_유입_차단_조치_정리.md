# STT 에코 유입 차단 조치 정리

## 문제
스피커폰 모드에서 상대방 음성이 단말 스피커로 재생될 때, 일부 소리가 마이크로 다시 유입되어 **내 로컬 STT**가 상대방 발화를 오인식하는 문제가 있었습니다.

## 목표
- 상대방 음성의 로컬 STT 유입 최소화
- 내 발화 인식률 유지
- 기존 라우팅 정책(블루투스 우선, 스피커폰 ON일 때만 스피커 사용) 유지

## 적용한 조치

### 1. 캡처 소스를 통화용 경로로 고정
- 파일: `app/src/main/java/org/duckdns/dorandoran/callaiassistant/webrtc/WebRtcManager.kt`
- 변경: `MediaRecorder.AudioSource.VOICE_RECOGNITION` -> `MediaRecorder.AudioSource.VOICE_COMMUNICATION`
- 의도: 통화 시나리오에서 플랫폼 AEC(에코 캔슬링) 경로를 우선 활용

### 2. 로컬 STT 에코 가드 조건을 라우트 기반으로 제한
- 파일: `app/src/main/java/org/duckdns/dorandoran/callaiassistant/webrtc/WebRtcManager.kt`
- 변경:
  - 라우트 상태 추적 필드 추가
  - `speakerRouteActive && !bluetoothRouteActive`일 때만 에코 가드 활성화
- 의도:
  - 블루투스/이어피스 구간에서 과도한 차단 방지
  - 스피커폰 환경에서만 에코 억제 적용

### 3. 완전 차단(drop)에서 감쇠(ducking) 방식으로 변경
- 파일: `app/src/main/java/org/duckdns/dorandoran/callaiassistant/webrtc/WebRtcManager.kt`
- 기존: 에코 우세로 판단되면 `FloatArray(0)` 반환(프레임 폐기)
- 변경: `localEchoGuardGain`(예: 0.22)으로 STT 입력 신호를 감쇠
- 의도:
  - 상대방 음성 유입은 줄이고
  - 내 발화가 작을 때도 완전히 사라지지 않도록 보존

### 4. 히스테리시스(hold/release) 추가
- 파일: `app/src/main/java/org/duckdns/dorandoran/callaiassistant/webrtc/WebRtcManager.kt`
- 변경:
  - 에코 판단 즉시 ON/OFF 대신 hold 시간(`~220ms`) 적용
  - gain 전환 시 스무딩 적용
- 의도:
  - 토글 떨림(chattering) 감소
  - 문장 중간 프레임 손실 완화

### 5. 스피커 토글 상태를 STT 처리 계층까지 전달
- 파일:
  - `app/src/main/java/org/duckdns/dorandoran/callaiassistant/MainActivity.kt`
  - `app/src/main/java/org/duckdns/dorandoran/callaiassistant/InCallActivity.kt`
- 변경: `callAudioManager.setSpeakerphone(isOn)`와 함께 `webRtcManager.setSpeakerphoneHint(isOn)` 호출
- 의도:
  - UI 토글 상태와 STT 에코 가드 조건 동기화

## 현재 동작 요약
- 블루투스 연결 시: 블루투스 우선, 에코 가드 비활성
- 블루투스 미연결 + 스피커폰 OFF: 이어피스, 에코 가드 비활성
- 블루투스 미연결 + 스피커폰 ON: 스피커폰, 에코 가드 활성(감쇠 + 히스테리시스)

## 후속 튜닝 포인트
- `echoLikely` 임계값(`remoteRms`, `localDominance`, `peak`)은 기기별 편차가 있어 로그 기반 튜닝 권장
- `localEchoGuardGain`(현재 약 0.22)은 인식률/에코억제 균형에 맞춰 조정 가능
