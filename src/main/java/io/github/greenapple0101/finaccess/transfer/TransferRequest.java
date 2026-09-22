// [이 파일의 역할: 이체 요청 한 건을 표현합니다]
// TransferStatus가 상태의 이름이라면, 이 클래스는 누가 어느 계좌에서 어디로 얼마를
// 보내려는지와 현재 상태를 함께 담는 상자입니다. HTTP 요청 본문용 DTO는 아닙니다.
// 생성자의 업무 규칙에 JPA 매핑을 추가했습니다. new만으로 저장되지는 않고 Repository가 필요합니다.
package io.github.greenapple0101.finaccess.transfer;

// UUID는 기존 계좌·사용자의 내부 ID에 쓰는 타입입니다.
// import는 짧은 이름으로 사용하도록 하는 선언이며 계좌를 조회하는 명령이 아닙니다.
import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

// public은 다른 패키지에서도 사용할 수 있다는 뜻, class는 객체의 구조와 행동을 정의하는 문법입니다.
// 같은 클래스로 서로 다른 이체 요청 객체를 여러 개 만들 수 있습니다.
// @Entity는 이 타입을 JPA 저장·조회 대상으로 표시합니다. Spring 서비스 Bean이라는 뜻은 아닙니다.
// @Table은 연결할 실제 테이블 이름을 지정합니다. 테이블 생성은 V4 SQL이 담당합니다.
@Entity
@Table(name = "transfer_requests")
public class TransferRequest {
    // 이체 요청 자체의 ID입니다. 계좌 ID·요청자 ID와는 별개의 식별자입니다.
    // @Id는 기본키, @GeneratedValue는 영속화할 때 Hibernate가 UUID를 생성하도록 합니다.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // JPA가 DB 값을 복원할 수 있게 final을 제거했습니다. 외부 수정용 setter는 여전히 없습니다.
    // @Column은 Java 필드와 DB 컬럼을 연결합니다. nullable=false는 필수 값이라는 매핑 정보입니다.
    // updatable=false는 JPA UPDATE에서 제외한다는 뜻입니다. 직접 SQL 수정까지 막는 보안 기능은 아닙니다.
    // 계좌 객체 전체 대신 UUID만 보관합니다. @ManyToOne 없이도 SQL 외래키 제약은 사용할 수 있습니다.
    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false, updatable = false)
    private UUID destinationAccountId;

    // Java long과 PostgreSQL BIGINT를 연결합니다. 단위는 원입니다.
    @Column(name = "amount_won", nullable = false, updatable = false)
    private long amountWon;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    // enum을 숫자 순번 대신 REQUESTED 같은 문자열로 저장합니다.
    // 선언 순서를 바꿔도 의미가 유지되지만 이름을 바꾸려면 DB 값도 고려해야 합니다.
    // 현재는 상태 변경 메서드가 없습니다. 승인 단계에서 전이 규칙을 연결합니다.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransferStatus status;

    // INSERT/UPDATE에 포함하지 않아 DB의 DEFAULT CURRENT_TIMESTAMP가 값을 정합니다.
    // 새 객체에 즉시 복사되는 설정은 아니므로 재조회 또는 refresh 후 확인합니다.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // JPA가 조회 결과로 객체를 복원할 때 사용하는 인자 없는 생성자입니다.
    // protected로 접근 범위를 좁히고 일반 코드에는 아래의 검증하는 생성자를 사용하게 합니다.
    // JPA 복원 시 업무용 생성자의 검증을 다시 실행하는 것은 아닙니다.
    protected TransferRequest() {
    }

    // [생성자 읽기]
    // 클래스와 이름이 같고 반환 타입이 없는 메서드 형태가 생성자입니다.
    // new TransferRequest(출금계좌ID, 입금계좌ID, 금액, 요청자ID)를 실행하면 호출됩니다.
    // 매개변수는 밖에서 받은 값이고 this.필드는 지금 만드는 객체 안에 저장할 값입니다.
    // 검증에 실패해 예외가 발생하면 정상적인 새 객체를 호출자에게 돌려주지 않습니다.
    public TransferRequest(UUID sourceAccountId, UUID destinationAccountId, long amountWon, UUID requesterId) {
        // null은 값이 없다는 뜻입니다. ID가 빠진 요청은 객체를 만들기 전에 거부합니다.
        if (sourceAccountId == null) {
            throw new IllegalArgumentException("Source account ID must be provided");
        }
        if (destinationAccountId == null) {
            throw new IllegalArgumentException("Destination account ID must be provided");
        }
        if (requesterId == null) {
            throw new IllegalArgumentException("Requester ID must be provided");
        }
        // UUID는 equals로 값이 같은지 비교합니다. 객체 참조를 비교하는 ==와 구분하세요.
        // 바로 위에서 null을 거부했으므로 안전하게 equals를 호출할 수 있습니다.
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }
        // long은 원 단위 정수입니다. 0원과 음수는 이체 요청으로 허용하지 않습니다.
        // 잔액 검사는 여기서 하지 않습니다. 요청 시 잔액이 충분해도 실행 전 바뀔 수 있습니다.
        // Long.MAX_VALUE까지 표현할 수 있지만 실제 거래 한도 정책은 아직 없습니다.
        if (amountWon <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        // 모든 검증을 통과한 뒤 전달받은 값을 객체의 필드에 저장합니다.
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amountWon = amountWon;
        this.requesterId = requesterId;
        // 호출자가 APPROVED나 COMPLETED를 넣을 수 있는 인자를 제공하지 않습니다.
        // 요청 생성과 승인은 별도 행동이어야 하므로 최초 상태를 여기서 정합니다.
        this.status = TransferStatus.REQUESTED;
    }

    // getter는 객체에 보관된 값을 읽어 반환하는 메서드입니다. DB 조회는 발생하지 않습니다.
    // setter(값을 덮어쓰는 메서드)는 제공하지 않습니다.
    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public long getAmountWon() { return amountWon; }
    public UUID getRequesterId() { return requesterId; }
    public TransferStatus getStatus() { return status; }
}
