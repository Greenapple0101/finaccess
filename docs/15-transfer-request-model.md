# 15. 이체 요청 한 건을 만드는 순서

## 첫 번째: 어떤 정보를 한 상자에 담을지 정합니다

지난 단계의 TransferStatus는 “진행 상황의 이름”만 정의했습니다.
이번 TransferRequest는 “이체하려는 내용 한 건”을 담습니다.

| 필드 | 의미 | 예시 |
| --- | --- | --- |
| sourceAccountId | 출금 계좌 ID | A 계좌의 UUID |
| destinationAccountId | 입금 계좌 ID | B 계좌의 UUID |
| amountWon | 원 단위 금액 | 1000 |
| requesterId | 요청한 업무 사용자 ID | AppUser의 UUID |
| status | 현재 진행 상태 | REQUESTED |

계좌나 사용자 정보를 복제하지 않고 기존 데이터의 ID를 보관합니다.
요청 자체의 ID와 생성 시각은 다음 DB 저장 단계에서 추가합니다.

## 두 번째: 생성자로 올바른 객체만 만듭니다

[TransferRequest.java](../src/main/java/io/github/greenapple0101/finaccess/transfer/TransferRequest.java)의
필드 선언 다음에 나오는 생성자를 읽으세요.

```java
TransferRequest request = new TransferRequest(sourceId, destinationId, 1000L, requesterId);
```

오른쪽 new가 객체 생성을 시작하고, 괄호 안 네 값이 생성자에 전달됩니다.
생성자는 필수 ID → 서로 다른 계좌인지 → 양수 금액인지 순서로 검사합니다.
모두 통과하면 필드에 저장하고 상태를 REQUESTED로 정합니다.
왼쪽 request 변수는 그렇게 만들어진 객체를 가리킵니다. DB INSERT는 실행되지 않습니다.

status를 생성자 인자로 받지 않으므로 요청 생성 시 승인을 건너뛸 수 없습니다.
private final 필드는 외부 직접 접근과 생성 후 재대입을 제한합니다.
현재는 상태까지 고정했으며, 승인 기능 단계에서 통제된 변경 메서드를 추가할 예정입니다.

## 세 번째: 검사할 수 있는 범위를 구분합니다

이 객체는 ID의 존재 여부(null인지)와 값의 형식적·기본 업무 규칙을 검사합니다.
그 ID에 해당하는 DB 행이 있는지는 알 수 없습니다.
출금 계좌가 요청자의 회사 소유인지, 로그인 사용자가 요청자인지도 아직 검사하지 않습니다.
이 검증은 실제 데이터를 조회하는 서비스와 인증 연동 단계에서 연결해야 합니다.

잔액도 여기서 차감하거나 예약하지 않습니다. 요청 이후 잔액이 바뀔 수 있으므로,
실제 이체 시점에 잔액 검사와 동시성 제어를 해야 합니다.
최대 거래 한도는 아직 정하지 않았고 현재는 양수 long 범위만 허용합니다.

## 네 번째: 테스트로 객체의 생성 규칙을 확인합니다

[TransferRequestTest.java](../src/test/java/io/github/greenapple0101/finaccess/transfer/TransferRequestTest.java)는
정상 생성, 필수 ID 누락, 동일 계좌, 0·음수 금액, 양수 경계값을 총 10회 검사합니다.

```bash
./gradlew test --tests '*TransferRequestTest'
```

이 테스트에는 Spring 실행이나 DB가 필요 없습니다.
이번 단계에서 만든 업무 코드 파일은 TransferRequest 하나입니다.
Controller → Service → Repository를 한꺼번에 만들지 않고,
이 객체를 다음에 SQL 테이블·JPA 저장소와 연결한 뒤 요청 서비스를 추가합니다.

> 이 문서는 15단계 당시의 순수 Java 모델을 설명합니다. 현재 코드는 [16단계](16-transfer-request-persistence.md)에서 JPA 매핑을 추가했고, final 필드를 제거하고 기본 생성자·요청 ID·생성 시각을 추가했습니다.
