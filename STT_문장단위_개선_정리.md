# STT 문장단위 개선 정리

## 목적
기존 STT 텍스트 표시에서 발생하던 문제를 줄이기 위해, 다음 기준으로 로직을 개선했다.

- 중간(interim) 인식 결과가 누적되며 중복 문장이 반복 표시되는 문제 개선
- final 결과 재작성(overwrite) 패턴을 안정적으로 반영
- 화자 전환(내 말 -> 상대 말, 상대 말 -> 내 말) 시 말풍선 경계 명확화
- AI 보정 모드에서 재녹음 시 이전 문장 잔존 문제 완화

## 참고 기준
개선 기준은 `backend/tools/index.html`의 처리 방식(특히 `start` 키 기반 final overwrite)이다.

핵심 아이디어:
- `finalChunks[startKey] = text` 형태로 같은 구간은 덮어쓰기
- 표시 텍스트는 `startKey` 오름차순 결합
- interim은 별도 상태로 관리 후 화면 반영

## 주요 변경 파일
- `app/src/main/java/org/duckdns/dorandoran/callaiassistant/stt/RealtimeTranscribeWsClient.kt`
- `app/src/main/java/org/duckdns/dorandoran/callaiassistant/webrtc/WebRtcManager.kt`
- `app/src/main/java/org/duckdns/dorandoran/callaiassistant/ui/viewmodel/CallViewModel.kt`

## 변경 내용 상세

### 1) WS Payload 메타 확장
`RealtimeSttPayload`에 아래 필드를 추가해 서버 이벤트 메타를 그대로 전달하도록 변경.

- `start: Double?`
- `duration: Double?`
- `speechFinal: Boolean`

`interim`/`final` 파싱 시 해당 값을 채워 ViewModel로 전달한다.

### 2) STT 업데이트 호출 방식 변경
기존에는 `payload.text`만 ViewModel에 전달했지만, 현재는 payload 전체와 `isFinal` 플래그를 전달한다.

- `updateMySttMessage(payload, isFinal)`
- `updateRemoteSttMessage(payload, isFinal)`

### 3) 텍스트 통화 STT 말풍선 처리 방식 변경
초기에는 문장(start) 단위 버블 분리 위주였으나, 최종적으로 **발화(turn) 단위 1버블 갱신** 방식으로 조정했다.

현재 규칙:
- 한 화자가 말하는 동안은 같은 말풍선에서 계속 텍스트 갱신
- `speech_final` 시점에 해당 버블을 확정하고 다음 발화는 새 버블
- 화자 전환이 감지되면 이전 화자 버블을 즉시 닫고, 새 화자는 새 버블 시작

내부적으로 화자별 상태를 둔다.

- `finalChunks`: final 결과 누적(같은 start는 overwrite)
- `currentStartKey`, `currentText`: 현재 interim 문장
- `activeBubbleIndex`: 현재 갱신 중인 말풍선 인덱스

### 4) AI 보정 모드(STT draft) 개선
AI 보정 모드 텍스트는 `committed + current` 구조로 표시한다.

- `final` 또는 `speech_final` 도착 시 committed에 반영
- interim은 current만 갱신
- 화면은 `committed + current` 형태로 표시
- 재시작 직후 이전 세션 잔여 이벤트 유입 완화를 위해 짧은 ignore window 적용

## 현재 기대 동작

### 텍스트 통화
- 문장 중간중간 버블이 쪼개지지 않고, 한 발화가 한 버블에서 갱신됨
- 화자 전환 시 버블 경계가 자연스럽게 분리됨

### AI 보정 모드
- 중복 누적 문장 표시 완화
- 재녹음 시작 시 이전 문장이 남는 현상 완화

## 남은 리스크 / 체크 포인트
- 네트워크 지연으로 out-of-order 이벤트가 들어올 때 start 없는 이벤트 처리 품질
- 짧은 발화가 연속되는 상황에서 `speech_final` 타이밍 품질
- 실제 단말에서의 재현 테스트 필요

## 권장 검증 시나리오
1. 내가 한 문장을 길게 말하는 동안 말풍선이 1개에서 계속 갱신되는지
2. 내가 말한 직후 상대가 말할 때 내 버블이 고정되고 상대 버블이 새로 생성되는지
3. AI 보정 모드에서 녹음 중지 후 재시작 시 이전 문장이 남지 않는지
4. 같은 문장을 반복 발화했을 때 과도한 중복 표시가 없는지

