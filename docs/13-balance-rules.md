# 13. Account에 입금·출금 규칙 추가하기

## 만든 순서

새 Controller나 Service를 만들지 않았습니다. 기존 Account에 상태 변경 메서드를 추가하고
실패를 표현할 InsufficientBalanceException, 단위 테스트, DB 변경 감지 테스트를 작성했습니다.
테이블 구조가 같으므로 V1~V3 SQL은 수정하지 않았습니다.

## 1. 입금 규칙

```java
public void deposit(long amountWon) {
    requirePositiveAmount(amountWon);
    balanceWon = Math.addExact(balanceWon, amountWon);
}
```

금액을 먼저 검사합니다. 0과 음수는 거부합니다.
addExact는 long 범위를 넘으면 ArithmeticException을 던집니다.
일반적인 +는 범위 초과를 자동으로 예외 처리하지 않습니다.
대입문은 오른쪽 계산이 성공한 후에 값을 바꾸므로 실패해도 기존 잔액은 유지됩니다.

## 2. 출금 규칙

```java
public void withdraw(long amountWon) {
    requirePositiveAmount(amountWon);
    if (amountWon > balanceWon) {
        throw new InsufficientBalanceException();
    }
    balanceWon -= amountWon;
}
```

양수인지 확인하고, 잔액보다 많으면 예외로 중단합니다.
`-=`는 현재 값에서 오른쪽 값을 빼서 다시 저장하는 연산입니다.
잔액과 같은 금액의 출금은 허용하므로 전액 출금 후에는 0원이 됩니다.

## 3. 객체 수정과 DB 반영은 다릅니다

```java
Account account = new Account(company);
account.deposit(1000L);
account.withdraw(300L);
```

이 코드만으로는 메모리의 객체가 700원이 됩니다. 새로운 객체라면 저장 작업이 필요합니다.
반면 트랜잭션 안에서 조회·저장되어 JPA가 관리 중인 엔티티는 변경을 추적합니다.
flush 시 변경된 잔액을 UPDATE로 반영하는 것이 변경 감지(dirty checking)입니다.
flush는 commit이 아닙니다. 트랜잭션이 롤백되면 DB 반영도 취소됩니다.

DB 테스트는 저장 → 입출금 → flush → clear → 재조회 순서로 700원을 확인합니다.
테스트 트랜잭션은 마지막에 롤백되므로 개발용 데이터에 영향을 주지 않습니다.

## 4. 확인한 경계

- 1,000원 입금 후 400원 출금: 600원
- 남은 600원 전액 출금: 0원
- 0원·음수 입출금: 거부, 잔액 유지
- 잔액보다 1원 큰 출금: 거부, 잔액 유지
- long 최대값에서 1원 추가 입금: 거부, 잔액 유지

```bash
./gradlew test
```

Long.MAX_VALUE를 이용한 테스트는 자료형의 한계를 검사합니다. 실제 거래 한도와는 다릅니다.

## 아직 구현하지 않은 것

입출금 HTTP API, 입금 출처 검증, 거래 원장, 이체의 원자성, 동시성 제어는 아직 없습니다.
두 요청이 같은 잔액을 동시에 읽는 문제는 이 객체의 검사만으로 해결되지 않습니다.
입금·출금을 외부에 노출하기 전에 업무 흐름과 거래 기록·권한·잠금 정책을 연결해야 합니다.
현재 메서드는 이후 이체 처리에서 사용할 부품입니다.

## 읽을 파일

- [Account](../src/main/java/io/github/greenapple0101/finaccess/account/Account.java)의 deposit, withdraw
- [AccountTest](../src/test/java/io/github/greenapple0101/finaccess/account/AccountTest.java)
- [AccountPersistenceTest](../src/test/java/io/github/greenapple0101/finaccess/account/AccountPersistenceTest.java)의 dirtyCheckingPersistsBalanceChanges

## 생각해 볼 질문

- 잔액을 먼저 빼고 나중에 잔액 부족을 검사하면 어떤 문제가 생길까요?
- 메모리의 계좌가 바뀌었다고 DB도 반드시 바뀌었을까요?
- 계좌 두 개를 차례로 변경하다 두 번째에서 실패하면 첫 번째 변경은 어떻게 처리해야 할까요?
