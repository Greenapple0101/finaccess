# FinAccess

기업 자금관리·이체 승인 과정을 구현하며 Spring, 인증·인가, 금융 거래의 정확성을 학습하는 프로젝트입니다.
실제 자금과 고객정보를 사용하지 않는 모의 시스템입니다.

## 목표

- 금융 도메인: 이체 상태, 승인 통제, 중복 처리 방지, 동시성, 감사 기록을 구현합니다.
- 회사 발표: Keycloak과 Spring Security의 로그인·SSO·권한 검사 책임을 설명합니다.
- 개인 학습: 작은 기능을 구현하고 테스트하며 Spring의 동작을 이해합니다.
- 확장: Kubernetes와 Istio로 서비스 간 접근을 통제하고 부하·장애 상황을 실험합니다.

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
| Keycloak | 사용자 로그인, SSO, 역할 관리, 토큰 발급 |
| Spring Security | 로그인 연동, 보호 API의 인증과 접근 제어 |
| Spring 업무 로직 | 회사 소속, 자기 승인 금지, 잔액, 거래 상태 검증 |
| PostgreSQL | 업무 데이터 저장과 트랜잭션 |
| Istio — 2차 | 서비스 신원에 기반한 통신 통제와 mTLS |

1차는 Spring MVC·JPA 중심의 단일 업무 서버로 시작합니다.
WebClient·WebFlux는 외부 은행 연동의 필요와 학습 목적에 맞춰 단계적으로 적용합니다.
2차는 은행 연동 서비스를 분리하고 Kubernetes·Istio 환경을 추가합니다.

## 진행 방식

작은 목표 설명 → 구현 → 검증 → 학습 정리 → 의미 단위 커밋

- 기본 환경은 Docker Compose, 확장 환경은 Kubernetes·Istio로 구분합니다.
- 구현하지 않은 기능을 완료했다고 표시하지 않습니다.
- 성능 수치는 테스트 환경, 조건, 측정 결과와 함께 기록합니다.
- 비밀번호, 클라이언트 시크릿, 토큰은 저장소에 커밋하지 않습니다.

## 현재 상태

Spring Boot 첫 HTTP API, PostgreSQL 연결, Flyway 회사 테이블 마이그레이션을 구성했습니다. 업무 기능과 인증은 이후 단계에서 추가합니다.

## 개발 환경과 실행

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
