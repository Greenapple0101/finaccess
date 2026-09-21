# 11. 계좌와 원화 금액: 만든 순서대로 읽기

## 이번 범위

회사 소유의 모의 원화 계좌를 0원으로 생성하고 DB에 저장·조회합니다.
계좌 개설 HTTP API, 입출금, 실제 은행 계좌번호, 외화, 마이너스 통장은 아직 없습니다.
사용자의 소속 회사 검사도 이후 인증 단계에서 추가합니다.

## 1. 먼저 저장할 내용을 정했습니다

| 컬럼 | 의미 |
| --- | --- |
| id | 계좌 내부 UUID. 실제 은행 계좌번호가 아님 |
| company_id | 소유 회사의 외래키 |
| balance_won | 원 단위 정수 잔액 |
| created_at | DB가 기록하는 생성 시각 |

원 단위 정수만 다루므로 Java long과 PostgreSQL BIGINT를 사용합니다.
예를 들어 1500은 1,500원입니다. double/float는 이진 부동소수점으로 소수를 근사할 수 있어 사용하지 않습니다.
소수 단위 금액이나 외화가 필요해지면 통화·정밀도·반올림 정책을 함께 설계해야 합니다.

long은 무한한 정수가 아닙니다. 표현 범위가 있고 연산 중 범위를 넘을 수 있습니다.
현재는 잔액 생성만 하며 덧셈·뺄셈 메서드가 없습니다.
이후 입출금 구현 시 금액 양수 여부, 잔액 부족, 덧셈 오버플로를 검사해야 합니다.
자료형 최대값과 실제 금융 업무의 거래 한도도 별개입니다.

## 2. V3 SQL을 작성했습니다

[src/main/resources/db/migration/V3__create_accounts.sql](../src/main/resources/db/migration/V3__create_accounts.sql)

- 외래키로 존재하지 않는 회사 연결을 막습니다.
- DEFAULT 0은 잔액 생략 시 0원을 넣습니다.
- NOT NULL은 잔액 누락을 막습니다.
- CHECK(balance_won >= 0)은 직접 SQL에서도 음수 잔액을 거부합니다.

V1과 V2는 수정하지 않았습니다. V3가 회사·사용자 테이블 뒤에 적용됩니다.
이 제약만으로 동시 출금이나 중복 이체가 해결되지는 않습니다. 잠금·멱등성은 별도 단계입니다.

## 3. Account 객체를 만들었습니다

[src/main/java/.../account/Account.java](../src/main/java/io/github/greenapple0101/finaccess/account/Account.java)

```java
Account account = new Account(company);
```

이 코드는 객체만 생성합니다. 생성자에서 회사 존재 여부(null)를 확인하고 잔액을 0L로 설정합니다.
DB에 실제 존재하는 회사인지는 저장 시 외래키가 검증합니다.
Long이 아닌 long은 기본형이며 null을 담을 수 없습니다. L 접미사는 long 정수 리터럴입니다.

@ManyToOne은 여러 계좌가 하나의 회사에 소속된다는 관계입니다.
회사에서 계좌 목록을 보관하는 양방향 매핑은 현재 필요하지 않아 추가하지 않았습니다.

## 4. Repository를 만들었습니다

[src/main/java/.../account/AccountRepository.java](../src/main/java/io/github/greenapple0101/finaccess/account/AccountRepository.java)

```java
Account saved = accountRepository.save(new Account(company));
accountRepository.findById(saved.getId());
```

기존 회사 기능처럼 Spring Data JPA의 기본 저장·조회 기능을 사용합니다.
현재 단계에는 Service와 Controller가 없습니다. 먼저 저장 모델을 확인한 뒤 HTTP 입구를 붙입니다.

## 5. 테스트로 확인했습니다

- 새 객체의 초기 잔액이 0인지
- 회사 없이 객체를 만들 수 없는지
- 같은 회사의 계좌 두 개를 저장·재조회할 수 있는지
- 직접 SQL로 넣은 음수 잔액이 거부되는지
- 존재하지 않는 회사에 계좌를 연결할 수 없는지
- SQL에서 잔액을 생략해도 0원이 되는지

```bash
./gradlew test
```

Testcontainers의 별도 PostgreSQL을 사용하므로 개발용 DB에 테스트 계좌가 남지 않습니다.

## 직접 테이블 확인

```bash
docker compose up -d --wait
./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081'
```

다른 터미널에서:

```bash
docker compose exec postgres psql -U finaccess -d finaccess -c '\d accounts'
```

## 다음 단계

계좌 개설 API를 추가합니다. Company 때와 같이 Service에서 회사를 확인하고
Account를 저장한 뒤 Controller가 결과를 HTTP 응답으로 전달하는 순서로 만들 예정입니다.
