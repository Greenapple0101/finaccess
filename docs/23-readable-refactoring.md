> **과거 학습 기록 — 현재 실행 가이드가 아닙니다.**
> 현재 OAuth2 최소 구성은 [문서 목차](README.md)에서 확인합니다. 이 문서의 금융·ITSM·DB·이전 인증 설정, 주소, 역할 이름과 캡처는 작성 당시의 자료입니다.
> 본문의 “현재”·“최신”도 당시 시점을 뜻합니다. 과거 검사 개수를 이번 검증으로 합산하지 않습니다. 현재 실행 코드는 두 저장소의 `oauth-lab/`이며 USER·ADMIN API 모두 API7을 경유합니다.

# [과거 기록] 23. 동작을 유지하면서 읽기 쉽게 바꾼 부분

이번에는 기능을 추가하지 않고, 한 줄씩 읽으며 작업 순서를 따라가기 쉽게 정리했습니다.
기존의 쉬운 한국어 주석은 유지하고, 바뀐 변수·메서드 이름에 맞춰 설명도 고쳤습니다.

## 1. 객체 만들기와 저장하기를 나눴습니다

이전 회사 등록 코드는 한 줄에 두 가지 일을 적었습니다.

```java
Company company = companyRepository.save(new Company(name));
```

이제는 위에서 아래로 순서대로 읽습니다.

```java
Company newCompany = new Company(name);
Company savedCompany = companyRepository.save(newCompany);
```

첫 줄은 이름을 검사하며 회사 객체를 만듭니다. 둘째 줄은 저장을 요청하고 결과를 받습니다.
계좌 개설도 같은 방식입니다. 객체를 만들었다고 DB 저장까지 끝나는 것은 아니라는 점이 코드에 보이게 했습니다.
Controller에서도 서비스 결과를 먼저 변수로 받고, 다음 줄에서 HTTP 응답을 만듭니다.

## 2. 출금·입금 계좌를 처음부터 역할대로 부릅니다

이전에는 잠글 순서대로 first·second를 구한 뒤, 그중 어느 것이 출금 계좌인지 다시 골랐습니다.
이제는 한 번의 if로 잠금 순서를 나누고 바로 역할에 맞는 변수에 담습니다.

```java
boolean sourceAccountComesFirst = sourceAccountId.compareTo(destinationAccountId) < 0;
Account sourceAccount;
Account destinationAccount;

if (sourceAccountComesFirst) {
    sourceAccount = findAccountWithLock(sourceAccountId);
    destinationAccount = findAccountWithLock(destinationAccountId);
} else {
    destinationAccount = findAccountWithLock(destinationAccountId);
    sourceAccount = findAccountWithLock(sourceAccountId);
}
```

변수 이름은 **돈의 이동 방향**, 실행 줄 순서는 **잠금을 얻는 순서**를 나타냅니다.
반대 방향 이체가 와도 두 계좌는 같은 UUID 비교 순서로 잠급니다.

아래쪽에는 실제 업무 순서가 그대로 이어집니다.

```java
sourceAccount.withdraw(amountWon);
accountRepository.flush();
// 입금 범위 초과는 catch에서 업무 예외로 바꾸고 다시 던집니다.
destinationAccount.deposit(amountWon);
transfer.complete(actorId);
```

위 코드는 읽는 순서를 보여 주기 위해 try/catch 부분을 줄인 예시입니다. 전체 코드는 TransferWorkflowService를 보세요.
flush는 DB에 출금 SQL을 보내지만 최종 확정은 아닙니다. 뒤에서 실패하면 출금도 취소됩니다.

## 3. 이름을 보고 역할을 알 수 있게 했습니다

| 이전 | 현재 | 뜻 |
| --- | --- | --- |
| transfers / accounts / users | transferRepository / accountRepository / userRepository | DB 저장소 객체임을 이름에 표시 |
| reviewable | findTransferForReview | 결재할 요청을 찾아 회사 소속까지 확인 |
| lockedAccount | findAccountWithLock | 잠금을 얻어 계좌 조회 |
| result | toResult | 엔티티의 필요한 값을 결과 데이터로 변환 |
| first / second → source / destination | sourceAccount / destinationAccount | 출금 계좌·입금 계좌를 직접 구분 |

회사 비교는 이미 찾은 사용자와 계좌를 checkSourceCompany에 전달해 수행합니다.
이 메서드는 두 회사 ID만 비교하며, 다른 조회나 저장을 하지 않습니다.
execute에서는 먼저 잠근 계좌를 그대로 전달하므로 잠금 전에 잔액을 일반 조회하지 않습니다.

## 4. 같은 파일 안에서 흐름을 따라갑니다

TransferController는 요청 처리 메서드 → 오류 응답 메서드 → 입력 데이터 모양(record) 순서로 모았습니다.
입력 필드와 URL은 그대로입니다. `@PathVariable("id")`로 주소의 id가 transferId 변수에 들어간다는 관계를 명시했습니다.

짧은 상태 switch, 필요한 record, 상태를 보호하는 엔티티 메서드는 그대로 사용합니다.
새 계층이나 공통 부모 클래스를 추가하지 않았습니다.
테스트에서는 중첩 조건식을 if로 풀고, 반복되는 결재·실행의 입력 JSON 만들기를 작은 도우미로 모아 검증 내용을 찾기 쉽게 했습니다.

## 5. 그대로 유지해야 하는 규칙

- 요청 행 잠금 → 상태 검사 → 사용자 조회 → 계좌 잠금 순서
- UUID 비교에 따른 두 계좌의 잠금 순서
- 출금 → flush → 입금 → 완료를 묶는 트랜잭션 범위
- 자기 결재·다른 회사 처리 금지와 각 오류 응답
- 요청 및 응답 JSON, 금액 단위, DB 스키마

기존 127개 테스트의 정상·오류·롤백·동시 처리 사례로 회귀를 확인합니다.
이 테스트가 모든 가능한 상황을 증명하는 것은 아니므로 코드 검토에서도 위 순서를 비교했습니다.
