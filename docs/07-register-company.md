# 07. 회사 등록 API와 계층의 역할

## 이번 단계의 질문

HTTP로 보낸 회사 이름은 어떤 과정을 거쳐 DB에 저장될까요?

POST /api/companies → Controller → Service → Repository → PostgreSQL

## 계층을 나누는 이유

| 계층 | 이번 코드의 책임 |
| --- | --- |
| CompanyController | JSON 요청을 받고 성공·실패 HTTP 응답 구성 |
| CompanyService | 등록 작업 실행과 트랜잭션 경계 |
| CompanyRepository | JPA를 이용한 저장 |
| Company | 이름이 비어 있거나 100자를 넘는 객체 생성 방지 |

Controller의 생성자로 Service를, Service의 생성자로 Repository를 전달받습니다.
이를 생성자 주입이라고 합니다. Spring이 등록된 객체를 찾아 연결해 줍니다.
`@Service`는 이 클래스를 Spring이 관리하는 컴포넌트로 등록합니다.

## 요청과 응답

```http
POST /api/companies
Content-Type: application/json

{"name":"핀액세스 데모"}
```

성공하면 201 Created와 다음 형태의 JSON을 반환합니다.

```json
{"id":"실제 생성된 UUID","name":"핀액세스 데모"}
```

`RegisterCompanyRequest`는 요청 데이터, `RegisteredCompany`는 등록 결과를 담습니다.
DB 엔티티를 그대로 HTTP 응답으로 노출하지 않습니다.
이번 응답에는 생성 시각을 포함하지 않으므로 DB 생성값을 읽기 위한 추가 조회도 하지 않습니다.
회사명은 고유 식별자가 아니므로 같은 이름의 회사 등록을 허용합니다.
중복 HTTP 요청 방지 기능은 아직 없습니다.

## 실패 응답

회사 이름이 누락되거나 null·공백이거나 100자를 넘으면 Company의 생성자가 거부합니다.
Controller의 예외 처리 메서드가 이를 400 Bad Request와 ProblemDetail로 변환합니다.
ProblemDetail은 status, title, detail 등으로 오류를 표현하는 Spring의 타입입니다.
깨진 JSON은 Spring MVC가 역직렬화 단계에서 거부하며, 이 경우도 400입니다.
모든 종류의 실패 본문 형식을 통일한 단계는 아닙니다.

현재 IllegalArgumentException 처리 범위는 CompanyController입니다.
오류 종류가 늘면 업무 예외를 구분하여 상태 코드와 응답 계약을 확장합니다.

## 트랜잭션

`CompanyService.register`의 `@Transactional`이 등록 작업의 트랜잭션을 관리합니다.
Spring의 프록시가 메서드 호출을 감싸 트랜잭션을 시작하고, 성공하면 커밋합니다.
기본적으로 RuntimeException 등으로 실패하면 롤백합니다.
`save()` 호출 즉시 DB 커밋이 완료되었다고 생각하면 안 됩니다.
INSERT 실행이 flush 시점까지 지연될 수 있으며, 커밋은 트랜잭션 경계에서 일어납니다.

## 직접 실행

```bash
docker compose up -d --wait
./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081'
```

다른 터미널에서:

```bash
curl -i -X POST http://localhost:8081/api/companies \
  -H 'Content-Type: application/json' \
  -d '{"name":"핀액세스 데모"}'

curl -i -X POST http://localhost:8081/api/companies \
  -H 'Content-Type: application/json' \
  -d '{"name":"   "}'
```

첫 요청은 개발용 DB에 회사를 실제로 저장합니다. 두 번째 요청은 저장하지 않습니다.
현재 인증·인가 도입 전의 로컬 학습용 API입니다. 회사 등록 권한은 인증 단계에서 추가합니다.

## 테스트의 의미

MockMvc로 실제 Controller·Service·Repository를 연결하고 Testcontainers PostgreSQL로 검증합니다.
성공 테스트에는 테스트용 @Transactional을 붙이지 않습니다.
그래야 Service가 자신의 트랜잭션을 완료한 뒤 JDBC 조회로 저장 결과를 확인할 수 있습니다.
정상 등록, 이름 누락·null·빈 값·공백, 길이 초과, 깨진 JSON을 확인합니다.
실패 요청 전후 회사 수가 같아 DB에 저장되지 않았는지도 검사합니다.

## 생각해 볼 질문

- 요청을 처리하는 Controller에 모든 저장 코드를 넣으면 어떤 책임이 섞일까요?
- save 메서드 반환과 트랜잭션 커밋 완료는 왜 구분해야 할까요?
- 회사 이름이 같다는 이유만으로 중복 요청이라고 판단해도 될까요?
