# 05. Flyway와 첫 회사 테이블

## SQL 파일을 Git에 저장하는 이유

한 사람만 DB 도구에서 테이블을 만들면 다른 개발자의 DB에는 그 테이블이 없습니다.
SQL 변경 파일을 버전으로 관리하면 새 DB에서도 같은 구조를 재현할 수 있습니다.

## 적용 흐름

앱 시작 → DataSource 연결 → Flyway 이력 확인 → 미적용 SQL 실행 → 적용 결과 기록 → 서버 준비 완료

Spring Boot의 Flyway 자동 설정을 사용합니다. `schema.sql`로 별도 초기화하지 않습니다.

## 파일 이름 읽기

`src/main/resources/db/migration/V1__create_companies.sql`

- `db/migration`: 기본 탐색 위치
- `V1`: 첫 번째 버전
- `__`: 버전과 설명 사이의 밑줄 두 개
- `create_companies`: 변경 설명

적용한 V1 파일은 이후 수정하지 않습니다. 변경은 `V2__...sql` 같은 새 파일로 추가합니다.
Flyway는 파일 체크섬을 저장해 이미 적용된 파일의 변경을 감지합니다.

## 첫 테이블

| 컬럼 | 의미 |
| --- | --- |
| `id` | UUID 형식의 회사 식별자. 기본키로 중복·NULL을 방지 |
| `name` | 최대 100자의 회사명. NULL·빈 문자열·공백만 있는 이름 제한 |
| `created_at` | 생성 시각. 생략하면 DB가 현재 시각을 기록 |

회사명은 바뀌거나 같을 수 있으므로 식별자로 사용하지 않습니다.
UUID는 이후 애플리케이션에서 생성해 전달합니다.
`TIMESTAMPTZ`는 시점을 저장하고 조회 세션의 시간대에 맞춰 표시합니다.
회사 CRUD API, 사용자 소속, JPA 엔티티는 다음 단계입니다.

## 로컬에서 확인

```bash
docker compose up -d --wait
./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081'
```

다른 터미널에서:

```bash
docker compose exec postgres psql -U finaccess -d finaccess
```

```sql
\d companies
SELECT version, description, success FROM flyway_schema_history;
```

앱을 종료하고 다시 실행하면 V1을 다시 실행하지 않습니다.
테스트도 빈 PostgreSQL에서 테이블 생성, 데이터 저장·조회, 생성 시각 기본값,
적용 이력과 재실행 시 미적용 건수 0을 확인합니다.
테스트의 `@Transactional`은 테스트에서 삽입한 회사 데이터를 종료 후 롤백합니다.

## 생각해 볼 질문

- 회사 이름이 바뀌어도 id가 유지되어야 하는 이유는 무엇인가요?
- 이미 적용한 V1을 고치지 않고 V2를 추가하는 이유는 무엇인가요?

공식 문서: https://docs.spring.io/spring-boot/how-to/data-initialization.html
