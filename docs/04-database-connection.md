# 04. Spring에서 PostgreSQL에 연결하기

## 연결의 흐름

Spring → DataSource(연결 풀) → JDBC 드라이버 → PostgreSQL

- JDBC: Java 코드에서 관계형 DB와 통신하는 표준 인터페이스입니다.
- PostgreSQL 드라이버: 그 인터페이스로 PostgreSQL과 실제 통신합니다.
- DataSource: DB 연결을 제공합니다. 이번 구성에서는 HikariCP가 연결 풀을 관리합니다.
- 연결 풀: 요청마다 연결을 새로 만드는 비용을 줄이기 위해 연결을 재사용합니다.

아직 JPA는 추가하지 않았습니다. 먼저 연결과 SQL 실행을 확인합니다.

## 로컬 실행

이전 단계에서 만든 `.env`와 Compose DB를 사용합니다.

```bash
docker compose up -d --wait
./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081'
```

8080은 다른 앱이 사용할 수 있으므로 예시에서는 8081을 사용합니다.

`application.properties`는 공통 설정입니다. `local` 프로필은 여기에
`application-local.properties`를 추가 적용합니다.
local 설정은 `.env`를 properties 형식으로 명시적으로 읽습니다.
따라서 `.env`의 비밀번호를 Java 코드나 Git에 복사하지 않아도 됩니다.
`.env`는 `KEY=value` 형식으로 작성합니다.

local 프로필 이외의 환경에서는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 제공합니다.
비밀번호 기본값은 넣지 않았습니다. 설정 없이 실행하면 정상 시작하지 않는 것이 의도입니다.

## 자동 테스트

```bash
./gradlew test
```

Docker가 실행 중이어야 합니다. Testcontainers가 별도 PostgreSQL을 만들고,
동적으로 정해진 주소·계정 정보를 Spring 테스트 설정에 전달합니다.
테스트가 끝나면 해당 컨테이너를 정리합니다. Compose의 개발용 DB는 사용하지 않습니다.
`SELECT current_database()` 결과를 확인하여 실제 PostgreSQL 연결을 검증합니다.
기존 Hello API 테스트도 함께 유지합니다.

## 생각해 볼 질문

- DB가 켜져 있어도 비밀번호가 틀리면 앱이 연결할 수 있을까요?
- 테스트에서 localhost:15432를 그대로 사용하면 어떤 문제가 생길까요?
