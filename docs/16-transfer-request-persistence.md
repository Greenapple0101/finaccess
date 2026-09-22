# 16. 이체 요청을 DB에 저장하는 순서

## 1. SQL로 보관할 자리를 만듭니다

[V4 SQL](../src/main/resources/db/migration/V4__create_transfer_requests.sql)을 먼저 읽으세요.
transfer_requests 테이블의 한 행이 요청 한 건입니다. 요청 자체의 id와 생성 시각을 추가했습니다.
외래키 REFERENCES는 출금 계좌·입금 계좌·요청자가 실제로 존재하도록 제한합니다.
CHECK는 양수 금액, 서로 다른 계좌, 허용된 상태 이름을 검사합니다.
이전 마이그레이션은 그대로 두고 V4를 새로 추가했습니다.

외래키는 회사 소속이나 접근 권한을 검사하지 않습니다.
상태 CHECK도 상태 이름만 검사하며 이전 상태에서 이동 가능한지는 검사하지 않습니다.
Java 업무 규칙, DB 제약, 로그인 권한은 각각 검사하는 범위가 다릅니다.

## 2. 기존 객체에 DB 주소를 붙입니다

[TransferRequest](../src/main/java/io/github/greenapple0101/finaccess/transfer/TransferRequest.java)에
@Entity와 @Table을 붙여 JPA 저장 대상으로 지정했습니다.
@Column은 필드를 컬럼에 연결하고, @Id와 @GeneratedValue는 요청 자체의 UUID를 정의합니다.
@Enumerated(EnumType.STRING)은 REQUESTED라는 이름으로 상태를 저장합니다.

기존 UUID 필드를 유지했습니다. @ManyToOne이 있어야만 외래키를 만들 수 있는 것은 아닙니다.
이번 매핑은 ID만 읽으며 계좌 객체를 자동으로 가져오지 않습니다.

JPA가 필드에 DB 값을 복원할 수 있도록 final을 제거하고 protected 기본 생성자를 추가했습니다.
수정용 setter는 없고 요청 내용은 updatable=false로 JPA UPDATE에서 제외합니다.
이 설정이 직접 SQL 수정이나 접근 권한까지 제한하지는 않습니다.
상태 변경은 다음 승인 단계에서 전용 메서드로 추가할 예정입니다.

## 3. 저장소 계약을 만듭니다

[TransferRequestRepository](../src/main/java/io/github/greenapple0101/finaccess/transfer/TransferRequestRepository.java)는
JpaRepository<TransferRequest, UUID>를 상속합니다.
Spring Data JPA가 저장소 구현을 제공하므로 save, findById 등을 사용할 수 있습니다.

new → 생성자 검사 → Repository.save → SQL 반영 → 트랜잭션 commit 순으로 구분하세요.
new만으로 DB에 저장되지 않습니다. saveAndFlush도 commit을 뜻하지 않습니다.

## 4. 별도 PostgreSQL로 확인합니다

[저장 테스트](../src/test/java/io/github/greenapple0101/finaccess/transfer/TransferRequestPersistenceTest.java)는
회사·두 계좌·요청자를 먼저 저장하고 이체 요청을 저장합니다.
flush 후 clear로 관리 객체를 분리하고 재조회하여 메모리의 기존 객체만 확인하는 일을 피합니다.
금액·각 ID·상태·DB 생성 시각을 확인하고 계좌 잔액이 0원으로 유지되는지도 검사합니다.
잘못된 SQL 입력의 거부와 DB 기본값까지 새 테스트 9회로 확인합니다.
테스트 트랜잭션은 끝날 때 롤백되며 개발용 Compose DB에는 영향을 주지 않습니다.

```bash
./gradlew test
```

Docker가 필요합니다. 개발용 DB에는 다음 로컬 앱 시작 시 Flyway가 V4를 적용합니다.
현재 공개 이체 요청 API는 없습니다. 다음 단계는 실제 계좌·사용자를 조회하고
요청자의 회사와 출금 계좌의 소속을 검사한 뒤 저장하는 서비스입니다.
인증 연동 후에는 요청자 ID를 임의 입력이 아니라 검증된 로그인 신원에서 얻어야 합니다.
