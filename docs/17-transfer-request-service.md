# 17. 이체 요청 서비스를 만든 순서

## 1. 기존 객체가 모르는 것을 찾습니다

TransferRequest 생성자는 양수 금액과 서로 다른 계좌 ID를 검사합니다.
DB에 실제 사용자가 있는지, 출금 계좌가 요청자의 회사 소유인지는 저장소 조회가 필요합니다.
이번에는 이 순서를 조합하는 TransferRequestService를 추가했습니다.

## 2. 필요한 저장소를 생성자로 받습니다

[서비스 코드](../src/main/java/io/github/greenapple0101/finaccess/transfer/TransferRequestService.java)의
필드 세 개는 사용자·계좌·이체 요청 저장소를 가리킵니다.
@Service를 읽은 Spring이 객체를 만들면서 생성자에 이 저장소들을 전달합니다.
각각 별도의 서버가 아니라 같은 앱 안에서 서로 사용하는 객체입니다.

## 3. request 메서드를 위에서 아래로 읽습니다

1. new TransferRequest: null ID·같은 계좌·0원·음수 금액을 검사합니다.
2. 사용자 조회: 없으면 TransferReferenceNotFoundException을 던집니다.
3. 출금 계좌 조회: 없으면 같은 종류의 예외를 던집니다.
4. 회사 ID 비교: 요청자와 출금 계좌의 소속이 다르면 TransferCompanyMismatchException을 던집니다.
5. 입금 계좌 조회: 실제로 존재하는지 확인합니다.
6. 저장: REQUESTED 상태의 요청을 저장합니다.
7. 결과: 필요한 값만 RequestedTransfer record로 반환합니다.

입금 계좌는 다른 회사 소유도 허용합니다. 회사 간 송금 요청을 표현하기 위한 정책입니다.
출금 계좌의 잔액이 0원이어도 요청은 가능합니다. 아직 출금하거나 금액을 예약하지 않습니다.
실제 실행 시점에 잔액과 동시성을 검사해야 합니다.

## 4. 트랜잭션 경계를 이해합니다

@Transactional은 Spring 프록시를 통한 호출에서 조회·저장 작업을 트랜잭션으로 감쌉니다.
이번 서비스의 업무 예외는 RuntimeException을 상속하므로 기본 롤백 대상입니다.
새 트랜잭션을 시작한 경우 메서드 본문이 반환한 뒤 commit까지 성공해야 호출자가 결과를 받습니다.
단순히 new TransferRequestService로 만든 객체를 호출하면 이 처리는 자동 적용되지 않습니다.

## 5. 실제 commit을 확인합니다

[서비스 테스트](../src/test/java/io/github/greenapple0101/finaccess/transfer/TransferRequestServiceTest.java)에는
테스트용 @Transactional을 붙이지 않았습니다. 서비스 자체 트랜잭션이 끝난 뒤
별도 조회로 저장 결과와 생성 시각을 확인합니다. 테스트 전용 DB는 @AfterEach에서 정리합니다.
같은 회사·다른 회사 수취 계좌의 정상 요청, 다른 회사 출금 계좌 거부,
없는 참조 3가지, 잘못된 입력 6가지를 합쳐 12회 검사합니다.
거부된 경우 이체 요청 행이 생기지 않는지도 확인합니다.

```bash
./gradlew test
```

## 6. 인증과는 아직 연결되지 않았습니다

현재 서비스는 전달받은 requesterId를 기준으로 업무 관계만 검사합니다.
그 ID가 로그인한 본인이라는 보장은 아직 없습니다. 인증 단계에서 검증된 신원으로
업무 사용자를 찾고 그 ID를 서비스에 전달해야 합니다. 역할 검사도 아직 없습니다.
현재 이체 Controller는 없으며 다음 단계는 로컬 학습용 요청 API와 오류 응답 연결입니다.
