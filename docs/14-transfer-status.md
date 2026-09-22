# 14. 이체 상태를 만든 순서

이번에는 새로운 상자를 많이 만들지 않습니다. 업무 코드 한 파일과 테스트 한 파일입니다.

## 1. 먼저 업무 순서를 정합니다

계좌의 입출금 기능은 있지만, 담당자가 요청했다고 곧바로 출금하면 승인 절차가 없습니다.
따라서 이체 한 건이 지금 어디까지 진행됐는지 표현할 이름부터 정합니다.

| 이름 | 뜻 | 다음에 허용되는 상태 |
| --- | --- | --- |
| REQUESTED | 요청됨 | APPROVED 또는 REJECTED |
| APPROVED | 승인됨 | COMPLETED |
| REJECTED | 반려됨 | 없음 |
| COMPLETED | 완료됨 | 없음 |

이번 프로젝트의 첫 규칙입니다. 승인 후 반려나 완료 후 취소는 지원하지 않습니다.
반려된 건을 되살리지 않고 새 요청을 만들도록 정했습니다.

## 2. 이름을 담을 enum을 만듭니다

[TransferStatus.java](../src/main/java/io/github/greenapple0101/finaccess/transfer/TransferStatus.java)를 여세요.
`enum`은 정해진 값 중 하나만 선택하게 하는 Java 문법입니다.
`TransferStatus status = TransferStatus.REQUESTED;`는
“TransferStatus 타입의 status 변수에 요청됨이라는 값을 담는다”는 뜻입니다.

이 파일에 Controller, Service, Repository를 붙일 필요는 없습니다.
아직 HTTP 요청이나 DB 저장을 다루지 않고, 순수한 업무 규칙만 정의하기 때문입니다.

## 3. 허용된 다음 상태인지 검사하는 메서드를 붙입니다

```java
TransferStatus status = TransferStatus.REQUESTED;
status = status.transitionTo(TransferStatus.APPROVED);
// 오른쪽 메서드가 APPROVED를 반환하고, =가 그 값을 왼쪽 변수에 다시 담습니다.
status = status.transitionTo(TransferStatus.COMPLETED);
```

이 예시는 상태 규칙만 보여줍니다. 실제 돈이 이동하지 않습니다.
`transitionTo` 안에서는 현재 상태인 `this`를 보고 `switch`로 허용 여부를 계산합니다.
허용하면 목표 상태를 `return`하고, 허용하지 않으면 `throw`로 예외를 던집니다.

`REQUESTED → COMPLETED`는 승인을 건너뛰므로 거부됩니다.
`COMPLETED → COMPLETED`도 거부됩니다. 중복 API 호출을 안전하게 처리하는 멱등성은 나중에 구현합니다.
`transitionTo(...)`만 호출하고 반환값을 버리면 호출한 변수의 값은 그대로입니다.
enum 자체를 바꾸는 메서드가 아니라 다음 값을 검사해서 돌려주는 메서드라는 점이 핵심입니다.

## 4. 테스트로 표 전체를 확인합니다

[TransferStatusTest.java](../src/test/java/io/github/greenapple0101/finaccess/transfer/TransferStatusTest.java)의
`@CsvSource`는 위 업무 표를 테스트 입력으로 적은 것입니다.
16가지 상태 조합과 각 상태에서 목표가 null인 4가지를 합쳐 20가지를 확인합니다.

```bash
./gradlew test --tests '*TransferStatusTest'
```

이 테스트만 실행하면 Spring 서버나 DB가 필요 없습니다.
전체 `./gradlew test`에는 기존 DB 통합 테스트도 있어서 Docker가 필요합니다.

## 5. 다음 상자는 이체 요청 객체입니다

이번 코드는 전이 순서만 제한합니다. 승인자 권한, 요청자와 승인자가 다른지,
잔액이 실제로 이동했는지, DB에 저장됐는지는 검사하지 않습니다.
다음에는 이체 한 건의 출금 계좌·입금 계좌·금액·요청자·현재 상태를 묶는 모델을 만듭니다.
그 객체가 상태 필드를 보관하고 이 메서드를 사용해야 규칙이 실제 이체와 연결됩니다.
완료 상태의 저장은 이후 출금·입금과 같은 트랜잭션에서 처리해야 합니다.
