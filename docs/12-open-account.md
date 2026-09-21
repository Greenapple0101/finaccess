# 12. 계좌 개설 API: 저장 기능 앞에 HTTP 입구 붙이기

## 만든 순서

이전 단계에서 Account와 AccountRepository를 만들었습니다.
이번에는 AccountService → AccountController → API 테스트 순서로 추가했습니다.
DB 구조가 바뀌지 않아 새 마이그레이션은 없습니다.

## 1. AccountService: 작업 묶기

```java
@Transactional
public OpenedAccount open(UUID companyId) {
    Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new CompanyNotFoundException(companyId));
    Account account = accountRepository.save(new Account(company));
    return new OpenedAccount(account.getId(), company.getId(), account.getBalanceWon());
}
```

회사 확인 → 객체 생성 → 저장 → 결과 구성 순서입니다.
없는 회사면 예외가 발생하므로 저장 단계로 넘어가지 않습니다.
@Transactional은 이 작업이 정상 완료되면 커밋합니다.
회사 존재 확인은 요청자의 소속·권한 확인과 다릅니다. 현재 API는 인증 전의 로컬 학습용입니다.

## 2. AccountController: 요청 연결

```http
POST /api/companies/{companyId}/accounts
```

@PathVariable이 URL의 회사 UUID를 받습니다. 요청 본문은 필요하지 않습니다.
초기 잔액을 요청 값으로 받지 않으므로 임의 금액으로 계좌를 개설할 수 없습니다.
현재 구현은 전송된 추가 본문을 사용하지 않으며, 거부하는 방식은 아닙니다.

| 결과 | 응답 |
| --- | --- |
| 정상 개설 | 201, id·companyId·balanceWon |
| 없는 회사 | 404 ProblemDetail |
| UUID로 변환할 수 없는 회사 ID | 400 |

같은 회사에 다시 개설 요청을 보내면 별도 계좌가 생성됩니다.
현재는 계좌 개설 멱등키나 개설 개수 제한이 없습니다.
CompanyController의 예외 처리기는 다른 Controller에 자동 적용되지 않으므로
AccountController에도 회사 없음 처리기를 추가했습니다.

## 3. 테스트

실제 테스트용 PostgreSQL에 회사를 저장한 뒤 MockMvc로 요청합니다.
성공 응답 뒤 JDBC로 계좌가 올바른 회사에 0원으로 커밋됐는지 확인합니다.
추가 본문에 잔액을 넣어도 0원으로 생성되는지, 없는 회사·잘못된 ID 요청은 저장하지 않는지도 검증합니다.

## 직접 실행

```bash
docker compose up -d --wait
./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081'
```

다른 터미널에서 회사부터 등록합니다.

```bash
curl -i -X POST http://localhost:8081/api/companies \
  -H 'Content-Type: application/json' \
  -d '{"name":"계좌 연습 회사"}'
```

반환된 id를 아래 `회사UUID` 자리에 넣습니다.

```bash
curl -i -X POST http://localhost:8081/api/companies/회사UUID/accounts
```

응답의 balanceWon은 0입니다. id는 계좌의 내부 식별자이며 실제 은행 계좌번호가 아닙니다.
실습 요청은 개발용 DB에 실제 행을 만듭니다. 앱을 재실행해도 데이터는 남습니다.

## 읽을 파일

- [AccountService](../src/main/java/io/github/greenapple0101/finaccess/account/AccountService.java)
- [AccountController](../src/main/java/io/github/greenapple0101/finaccess/account/AccountController.java)

## 생각해 볼 질문

- 계좌를 저장하기 전에 회사를 조회하는 이유는 무엇인가요?
- 초기 잔액을 클라이언트 입력으로 받지 않는 이유는 무엇인가요?
- 회사가 존재한다는 사실만으로 계좌 개설 권한을 판단할 수 있을까요?
