# FinAccess

**코드 읽기가 처음이라면 [쉬운 설명과 코드 읽는 순서](docs/00-reading-guide.md)부터 시작하세요.**

**현재 인증 구조는 [24. JWT 검증과 업무 사용자 연결](docs/24-jwt-resource-server.md)을 확인하세요.** 이전 단계 문서는 당시의 구현 과정을 기록하므로, 인증 도입 전 요청 예시는 현재 API와 다를 수 있습니다.

소스와 테스트 주석은 파일의 역할 → 실행 순서 → 구체적인 예시 순으로 설명합니다. [DB 변경 파일 쉬운 해설](docs/22-database-reading-guide.md)도 함께 볼 수 있습니다.

기업 자금관리·이체 승인 과정을 구현하며 Spring, 인증·인가, 금융 거래의 정확성을 학습하는 프로젝트입니다.
실제 자금과 고객정보를 사용하지 않는 모의 시스템입니다.

## 목표

- 금융 도메인: 이체 상태, 승인 통제, 중복 처리 방지, 동시성, 감사 기록을 구현합니다.
- 회사 발표: Keycloak·Apache APISIX·독립적인 두 Spring Resource Server의 JWT 검증과 권한 검사 책임을 설명합니다.
- 개인 학습: 작은 기능을 구현하고 테스트하며 Spring의 동작을 이해합니다.
- 이번 범위: FinAdmin과 FinAccess를 각각 실행하고 Gateway와 각 백엔드의 인증·인가를 검증합니다.
- 향후 별도 범위: Kubernetes·Istio·부하 실험. SSO 시연은 이번 목표에 포함하지 않습니다.

## 첫 번째 완성 범위

담당자 로그인 → 회사 계좌 조회 → 이체 요청 → 다른 결재자 승인 또는 반려 → 모의 내부 이체 → 감사 조회

| 역할 | 할 수 있는 일 |
| --- | --- |
| 자금 담당자 | 소속 회사 계좌 조회와 이체 요청 |
| 결재자 | 소속 회사 이체 요청 승인·반려 |
| 감사 담당자 | 소속 회사 거래·승인 기록 조회 |

### 반드시 지킬 규칙

1. 다른 회사의 계좌와 거래에 접근할 수 없습니다.
2. 자신의 이체 요청을 자신이 승인할 수 없습니다.
3. 동일 요청을 재전송해도 중복 이체가 발생하지 않습니다.
4. 동시 승인에도 이체는 한 번만 실행됩니다.
5. 잔액 검증과 출금·입금 기록은 원자적으로 처리합니다.
6. 실패한 내부 이체는 일부 금액만 반영된 상태로 남지 않습니다.
7. 이체와 승인 상태 변화의 수행자·시간·결과를 기록합니다.

## 기술의 책임

| 구성 | 책임 |
| --- | --- |
| Keycloak | 사용자 로그인, API별 역할 관리, 액세스 토큰 발급 |
| Apache APISIX | 토큰 검증, API 라우팅, Bearer 토큰 전달 |
| Spring Security | 백엔드 자체의 JWT 검증, 클라이언트 역할에 따른 API 접근 제어 |
| Spring 업무 로직 | 회사 소속, 자기 승인 금지, 잔액, 거래 상태 검증 |
| PostgreSQL | 업무 데이터 저장과 트랜잭션 |
| Istio — 2차 | 서비스 신원에 기반한 통신 통제와 mTLS |

FinAccess는 Spring MVC·JPA로 업무 데이터를 처리합니다. 별도 [FinAdmin](https://github.com/Greenapple0101/finadmin)은 관리자 API와 통합 실습 환경을 제공합니다. 각 앱은 Gateway와 별개로 토큰을 검증합니다.

## 진행 방식

작은 목표 설명 → 구현 → 검증 → 학습 정리 → 의미 단위 커밋

- 기본 환경은 Docker Compose, 확장 환경은 Kubernetes·Istio로 구분합니다.
- 구현하지 않은 기능을 완료했다고 표시하지 않습니다.
- 성능 수치는 테스트 환경, 조건, 측정 결과와 함께 기록합니다.
- 비밀번호, 클라이언트 시크릿, 토큰은 저장소에 커밋하지 않습니다.

## 현재 상태

회사 등록·조회 API, 사용자 소속 모델, 계좌 개설 API와 입출금 규칙을 구현했습니다. 이체 상태 전이 규칙도 정의했습니다. 이체 요청·승인·반려·실행 API를 구현했습니다. JWT 검증, API별 역할 검사와 검증된 신원에 따른 회사 접근 제한을 추가했습니다.

## 개발 환경과 실행

**Keycloak·APISIX·두 백엔드를 함께 실행하려면 [FinAdmin 통합 실행 가이드](https://github.com/Greenapple0101/finadmin#실행하기)를 사용하세요.** 아래 명령은 FinAccess만 로컬에서 실행하는 방법입니다. 통합 실습에서는 FinAccess 직접 호출 포트가 18082이고, Gateway 주소는 `http://localhost:18090/access/`입니다.

- Java 21
- Spring Boot 4.1.1
- Gradle Wrapper 사용: Gradle을 별도로 설치할 필요가 없습니다.
- 앱 실행에는 PostgreSQL이 필요합니다. 테스트는 Docker에서 별도 PostgreSQL을 자동으로 실행합니다.

```bash
./gradlew test
./gradlew bootRun --args='--spring.profiles.active=local'
```

기본 포트는 8080입니다. 종료는 실행 터미널에서 `Ctrl+C`를 누릅니다.
포트가 사용 중이면 `./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081'`로 변경할 수 있습니다.

실행 가능한 JAR 파일은 `./gradlew bootJar`로 생성합니다.

## 학습 순서

[작은 단계별 로드맵](docs/learning-roadmap.md)

## 첫 API 확인

서버 실행 후 다른 터미널에서 호출합니다.

```bash
curl -i http://localhost:8080/api/hello
```

HTTP 200과 JSON `{"message":"Hello, FinAccess!"}`을 반환합니다.
이 API는 첫 요청·응답을 학습하기 위한 공개 예제입니다.

## 단계별 설명

- [01. 앱의 시작점과 Gradle](docs/01-first-application.md)
- [02. 첫 API와 HTTP 응답 테스트](docs/02-first-api.md)

## 로컬 PostgreSQL

Spring은 local 프로필에서 로컬 PostgreSQL에 연결합니다. 먼저 아래 DB 준비를 완료하세요.
처음 실행할 때 `.env.example`을 `.env`로 복사하고 `POSTGRES_PASSWORD`를 설정합니다.
기존 `.env`가 있으면 덮어쓰지 마세요.

```bash
cp -n .env.example .env
# .env의 POSTGRES_PASSWORD 값을 직접 입력한 다음 실행
docker compose up -d --wait
docker compose ps
```

접속 주소는 `localhost:15432`, DB와 사용자는 `finaccess`입니다.
종료는 `docker compose stop`을 사용합니다. 비밀번호는 Git에 커밋하지 않습니다.

[03. PostgreSQL·컨테이너·볼륨 설명](docs/03-local-postgresql.md)

- [04. Spring과 DB 연결](docs/04-database-connection.md)
- [05. Flyway와 첫 회사 테이블](docs/05-first-migration.md)

- [06. Company 엔티티와 JPA 저장소](docs/06-company-jpa.md)

- [07. 회사 등록 API와 계층의 역할](docs/07-register-company.md)

## 회사 등록 API

`POST /api/companies`에 `{"name":"핀액세스 데모"}`를 보내면 HTTP 201과 회사 id·name을 반환합니다.
잘못된 이름은 400으로 거부합니다. `finaccess-api`의 `admin` 역할이 필요합니다.
실행·요청 예시와 테스트 설명은 위 07 문서를 확인하세요.

## 회사 조회 API

`GET /api/companies/{id}`는 id·name·createdAt을 반환합니다.
일반 사용자는 업무 DB에 연결된 자기 회사만 조회합니다. 다른 회사는 403입니다. `admin`은 여러 회사 조회가 가능합니다. 허용된 조회에서 회사가 없으면 404, 잘못된 UUID는 400입니다.

- [08. 회사 조회와 200·400·404의 구분](docs/08-find-company.md)

## 업무 사용자와 회사 소속

`app_users`는 회사 한 곳에 소속되며, 인증 발급자와 사용자 식별자 조합으로 로그인 계정과 연결할 준비를 합니다.
검증된 JWT의 `iss + sub`로 이 표를 조회합니다. 업무 DB에 등록되지 않은 신원은 이체 업무에서 403으로 거부합니다. 사용자 등록 API는 없으며 통합 실습의 초기 데이터 준비 단계에서 연결합니다.

- [09. 업무 사용자와 회사 소속](docs/09-user-company.md)

## 모의 원화 계좌 모델

계좌는 회사 한 곳에 소속되며 0원으로 시작합니다. 잔액은 원 단위 정수이고 DB가 음수를 거부합니다.
모델·저장소와 계좌 개설 API를 구현했습니다. Account의 입출금 규칙까지 구현했으며 입출금 API는 아직 없습니다.

- [11. 계좌 모델을 만든 순서](docs/11-account-model.md)

## 계좌 개설 API

`POST /api/companies/{companyId}/accounts`는 본문 없이 호출하며, 201과 계좌 id·companyId·balanceWon(0)을 반환합니다.
`admin`만 개설할 수 있습니다. 관리자는 여러 회사의 계좌를 개설할 수 있으며, 없는 회사는 404, 잘못된 UUID는 400입니다.

- [12. 계좌 개설 API를 만든 순서](docs/12-open-account.md)

- [13. 입출금 규칙과 JPA 변경 감지](docs/13-balance-rules.md)

## 이체 상태 규칙

요청 → 승인 또는 반려, 승인 → 완료 순서를 정의했습니다. 이체 요청 엔티티와 승인·반려·실행 API에 연결했습니다.

- [14. 이체 상태를 만든 순서와 모든 상태 조합 테스트](docs/14-transfer-status.md)

## 이체 요청 객체

출금·입금 계좌 ID, 양수 금액, 요청자 ID를 묶는 TransferRequest를 추가했습니다. 동일 계좌 요청을 거부하고 최초 상태를 REQUESTED로 고정합니다. JPA 저장소와 DB 제약을 연결했으며 저장·재조회를 검증했습니다. 요청·승인·반려·실행 API와 회사 소속 검사를 연결했습니다. HTTP 진입점에 로그인 신원·역할 검사를 연결했습니다.

- [15. 이체 요청 객체를 만든 순서](docs/15-transfer-request-model.md)

- [16. 이체 요청 DB 저장과 재조회](docs/16-transfer-request-persistence.md)

## 이체 요청 서비스

요청자·계좌 존재 여부와 요청자의 회사가 출금 계좌 소유 회사인지 검사한 뒤 저장합니다. 수취 계좌는 다른 회사도 허용합니다. HTTP API와 연결했습니다. 요청자 ID는 Controller가 검증한 JWT 신원으로 찾습니다.

- [17. 이체 요청 서비스와 트랜잭션](docs/17-transfer-request-service.md)

- [18. 이체 요청 HTTP API와 오류 응답](docs/18-transfer-api.md)

- [19. 승인·반려와 결재자 분리](docs/19-transfer-review.md)

## 모의 이체 처리 API

| 요청 | 용도 | 성공 |
| --- | --- | --- |
| POST /api/transfers | 요청 생성 | 201 |
| POST /api/transfers/{id}/approve | 다른 사용자의 승인 | 200 |
| POST /api/transfers/{id}/reject | 다른 사용자의 반려 | 200 |
| POST /api/transfers/{id}/execute | 승인된 요청 실행 | 200 |

생성에는 `sourceAccountId`, `destinationAccountId`, `amountWon`만 전달합니다. `user` 또는 `admin` 역할이 필요합니다.
승인·반려·실행에는 본문 없이 Bearer 토큰을 전달합니다. `approver` 또는 `admin` 역할이 필요합니다.
`requesterId`·`actorId` 입력은 거부합니다. 수행자 ID는 검증된 토큰과 업무 DB의 연결에서 찾습니다.
입력 오류 400, 회사 불일치·자기 결재 403, 없는 참조 404, 상태·잔액 충돌 409입니다.
토큰 없음·유효하지 않은 토큰은 401, 역할 부족·미등록 업무 신원은 403입니다. 로컬 실습 환경이며 운영 배포의 TLS·네트워크 정책·비밀 관리 등은 별도로 준비해야 합니다.

- [20. 내부 이체·잠금·롤백 검증](docs/20-internal-transfer.md)

- [2026.09.29 내부 공유용 진행 현황](docs/21-internal-progress-2026-09-29.md)

- [23. 동작을 유지하며 읽기 쉽게 바꾼 코드](docs/23-readable-refactoring.md)

- [24. JWT 검증·역할 제한·업무 사용자 연결](docs/24-jwt-resource-server.md)
