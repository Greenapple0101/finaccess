> **과거 학습 기록 — 현재 실행 가이드가 아닙니다.**
> 현재 OAuth2 최소 구성은 [문서 목차](README.md)에서 확인합니다. 이 문서의 금융·ITSM·DB·이전 인증 설정, 주소, 역할 이름과 캡처는 작성 당시의 자료입니다.
> 본문의 “현재”·“최신”도 당시 시점을 뜻합니다. 과거 검사 개수를 이번 검증으로 합산하지 않습니다. 현재 실행 코드는 두 저장소의 `oauth-lab/`이며 USER·ADMIN API 모두 API7을 경유합니다.

# [과거 기록] 18. 이체 요청 HTTP API

만든 순서는 기존 Service → TransferController → HTTP 통합 테스트입니다.
실행 순서는 POST JSON → @RequestBody record → Service 검증·저장 → 201 JSON입니다.

POST /api/transfers에 다음 형태의 본문을 보냅니다. UUID는 DB에 실제로 존재하는 값이어야 합니다.

```json
{"sourceAccountId":"출금 계좌 UUID","destinationAccountId":"입금 계좌 UUID","amountWon":1000,"requesterId":"요청자 UUID"}
```

성공 응답에는 id, 두 계좌 ID, 금액, 요청자, REQUESTED 상태가 포함됩니다.
400은 필수값·금액·동일 계좌·JSON 형식 오류, 404는 없는 사용자·계좌,
403은 요청자와 출금 계좌의 회사 불일치입니다. 업무 오류는 ProblemDetail로 반환합니다.
금액은 Long으로 받아 null을 구분하며 소수 입력은 Jackson 설정으로 잘라내지 않고 거부합니다.
잘못된 JSON이나 UUID는 Controller 메서드 실행 전에 Spring MVC가 400으로 처리합니다.

아직 인증 없는 로컬 학습용 API입니다. 본문 requesterId는 실제 로그인 신원으로 검증되지 않습니다.
요청 생성은 출금·승인·금액 예약을 수행하지 않습니다.
테스트는 MockMvc와 별도 PostgreSQL로 응답 및 실제 저장 여부를 함께 확인합니다.
