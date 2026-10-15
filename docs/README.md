# FinAccess · 현재 프로젝트 문서

**2026-10-15 기준: FinAccess는 프런트와 USER API를 제공합니다.** 화면은 로컬 Spring 18100에서 받고 API 요청은 API7 18090을 거쳐 돌아옵니다. 현재 실행 코드는 `oauth-lab/`이며 `main`에도 병합되어 있습니다.

## 처음 읽는 순서

| 문서 | 내용 |
|---|---|
| [FinAccess 코드 읽기](current/01-code-reading.md) | 화면 → PKCE → 토큰 → API7 → Spring USER 검사 |
| [OAuth2 학습 순서](current/02-learning-roadmap.md) | 초보자가 요청 한 건씩 이해할 순서 |
| [전체 범위·유즈케이스](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/01-scope-use-cases.md) | FinAccess·FinAdmin·인증 서버의 책임 |
| [전체 아키텍처·시퀀스](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/02-architecture.md) | 현재 실행 주소와 로그인·API 흐름 |
| [API 명세·권한표](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/03-api-spec.md) | USER·ADMIN, 정상·실패 응답 |
| [Keycloak·LDAP](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/04-keycloak-ldap.md) | 사용자·그룹·department 연결 |
| [API7 설정](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/05-api7-authorization.md) | JWT·ACL·경로·메서드·CORS |
| [검증 결과·캡처](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/06-verification.md) | 실제 USER 200·ADMIN 403 화면과 날짜별 결과 |

## 실행·시연

- [FinAccess 실행 요약](../oauth-lab/README.md)
- [공통 RUNBOOK](https://github.com/Greenapple0101/finadmin/blob/main/oauth-lab/RUNBOOK.md)
- [10/15 API7 연결 검사](https://github.com/Greenapple0101/finadmin/blob/main/oauth-lab/RESULTS-2026-10-15.md)
- [10/14 프런트 이동 당시 기록](../oauth-lab/RESULTS.md): 당시 직접 호출 구성의 과거 기록.

금융·회사·계좌·이체 기능은 이전 학습 범위입니다. 현재 USER API는 검증된 신원·부서·역할을 응답하며 업무 DB를 조회하지 않습니다. ADMIN은 USER의 상위 Role로 자동 포함되지 않습니다.

## 이전 학습 기록

아래 링크는 학습 과정과 당시 검증 근거를 보관합니다. **현재 구성을 설치하거나 시연할 때는 위 문서만 사용합니다.** 옛 링크를 깨뜨리지 않도록 파일 위치를 유지하고 각 문서 맨 위에도 같은 구분을 표시했습니다.

<details>
<summary>과거 금융·ITSM·인증 학습 문서 펼치기</summary>

- [처음 읽는 FinAccess 코드](00-reading-guide.md)
- [01. Spring Boot 애플리케이션 시작하기](01-first-application.md)
- [02. 첫 API와 HTTP 응답 테스트](02-first-api.md)
- [03. PostgreSQL을 독립적으로 실행하기](03-local-postgresql.md)
- [04. Spring에서 PostgreSQL에 연결하기](04-database-connection.md)
- [05. Flyway와 첫 회사 테이블](05-first-migration.md)
- [06. Company 객체와 companies 테이블 연결하기](06-company-jpa.md)
- [07. 회사 등록 API와 계층의 역할](07-register-company.md)
- [08. 회사 ID로 조회하기](08-find-company.md)
- [09. 로그인 계정과 업무 사용자, 회사 소속](09-user-company.md)
- [SQL을 주석으로 읽기](10-sql-commentary.md)
- [11. 계좌와 원화 금액: 만든 순서대로 읽기](11-account-model.md)
- [12. 계좌 개설 API: 저장 기능 앞에 HTTP 입구 붙이기](12-open-account.md)
- [13. Account에 입금·출금 규칙 추가하기](13-balance-rules.md)
- [14. 이체 상태를 만든 순서](14-transfer-status.md)
- [15. 이체 요청 한 건을 만드는 순서](15-transfer-request-model.md)
- [16. 이체 요청을 DB에 저장하는 순서](16-transfer-request-persistence.md)
- [17. 이체 요청 서비스를 만든 순서](17-transfer-request-service.md)
- [18. 이체 요청 HTTP API](18-transfer-api.md)
- [19. 승인·반려를 연결한 순서](19-transfer-review.md)
- [20. 모의 내부 이체와 롤백](20-internal-transfer.md)
- [FinAccess 개발 진행 현황 공유](21-internal-progress-2026-09-29.md)
- [DB 변경 파일을 쉬운 말로 읽기](22-database-reading-guide.md)
- [23. 동작을 유지하면서 읽기 쉽게 바꾼 부분](23-readable-refactoring.md)
- [24. 토큰을 확인하고, 로그인한 사람으로 업무 처리하기](24-jwt-resource-server.md)
- [LDAP 직원 계정을 FinAccess 업무 사용자와 연결하기](25-ldap-identity.md)
- [작은 단계별 학습 로드맵](learning-roadmap.md)

</details>
