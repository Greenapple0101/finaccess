// [이 파일은 무엇을 하나요?]
// 이체 요청 한 건을 담습니다. "누가, 어느 계좌에서 어디로, 얼마를 보내려는가"를 기억합니다.
// 사용 순서: 생성자로 값 검사 → Repository로 저장 → Service가 승인·반려·완료 메서드 호출.
// HTTP 본문을 받는 양식과는 다릅니다. 이 객체는 업무 규칙을 지키면서 DB에 보관할 대상입니다.
// 예: 1,000원 요청을 만들면 REQUESTED로 시작하고, 다른 사람이 승인해야 APPROVED가 됩니다.
// 이 객체 혼자서는 DB 조회나 출금·입금을 하지 않습니다. 필요한 순서는 Service가 조율합니다.
package io.github.greenapple0101.finaccess.transfer;

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

// @Entity는 "이 객체를 DB에 저장하고 다시 읽을 수 있게 연결한다"는 표시입니다.
// @Table은 연결할 테이블 이름입니다. 실제 테이블 생성·변경은 Flyway의 V4·V5·V6 SQL이 담당합니다.
// Spring이 주입하는 Service 객체와 JPA가 저장하는 Entity 객체는 역할이 다릅니다.
@Entity
@Table(name = "transfer_requests")
public class TransferRequest {
    // 요청서 자체의 고유 번호입니다. 출금 계좌 번호나 요청자 번호와는 별개입니다.
    // @Id는 DB 행을 구별할 기본키 표시이고, @GeneratedValue는 저장할 때 UUID를 만들게 합니다.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 필드는 객체가 기억하는 값이고, 컬럼은 DB 테이블의 세로 항목입니다. @Column이 둘을 연결합니다.
    // nullable=false는 필수 값, updatable=false는 JPA가 UPDATE할 때 이 값을 제외한다는 뜻입니다.
    // 직접 SQL로 고치는 일까지 막는 보안 장치는 아닙니다.
    // 계좌 객체 전체가 아니라 ID만 저장합니다. 실제 계좌가 있는지는 Service와 DB 외래키가 확인합니다.
    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    // 돈을 받을 계좌의 ID입니다. 출금 계좌와 같을 수 없다는 검사는 생성자에서 합니다.
    @Column(name = "destination_account_id", nullable = false, updatable = false)
    private UUID destinationAccountId;

    // 원 단위 정수 금액입니다. 1,000원은 1000으로 저장합니다. 소수점 금액은 다루지 않습니다.
    @Column(name = "amount_won", nullable = false, updatable = false)
    private long amountWon;

    // 처음 요청서를 만든 사용자 ID입니다. 자기 요청을 자기가 결재하는지 비교할 때도 씁니다.
    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    // 상태를 0·1 같은 순번이 아니라 REQUESTED 같은 이름으로 저장합니다.
    // 그래야 enum 선언 순서를 바꿔도 기존 DB 값의 뜻이 달라지지 않습니다.
    // 상태 이름 자체를 바꿀 때는 DB에 이미 저장된 이름도 함께 고려해야 합니다.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransferStatus status;

    // 요청을 만든 시각은 DB의 기본값으로 기록합니다. Java의 INSERT/UPDATE 값 목록에서는 제외합니다.
    // DB가 정한 시각이 새 객체에 자동으로 바로 복사되는 설정은 아닙니다. 다시 읽으면 확인할 수 있습니다.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // 승인 또는 반려한 사람과 그 시각입니다. 아직 결재 전이면 두 값 모두 null(없음)입니다.
    // 누가 처리했는지를 기록할 뿐, 이 ID가 로그인으로 확인된 신원이라는 뜻은 아닙니다.
    @Column(name = "reviewer_id")
    private UUID reviewerId;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    // 실제로 모의 이체를 끝낸 시각과 실행자입니다. 승인한 시각·사람과 구별해서 저장합니다.
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "executed_by")
    private UUID executedBy;

    // JPA가 DB에서 읽은 값을 객체로 복원할 때 필요한 빈 생성자입니다.
    // 일반 업무 코드는 아래의 값 검사 생성자를 씁니다. JPA 복원 시 아래 검사를 다시 거치지는 않습니다.
    // 필드에 final이 없는 것도 JPA가 읽은 값을 채울 수 있게 하기 위해서입니다.
    protected TransferRequest() {
    }

    // new TransferRequest(출금계좌ID, 입금계좌ID, 1000L, 요청자ID)를 실행하면 여기로 옵니다.
    // 생성자는 새 객체를 준비하는 부분입니다. 필요한 값을 검사하고 모두 통과하면 객체 안에 보관합니다.
    // 검사 중 throw로 예외를 알리면 여기서 중단되어 정상적인 요청 객체를 돌려주지 않습니다.
    public TransferRequest(UUID sourceAccountId, UUID destinationAccountId, long amountWon, UUID requesterId) {
        // 먼저 세 ID가 빠지지 않았는지 검사합니다. null은 "값이 없다"는 뜻입니다.
        if (sourceAccountId == null) {
            throw new IllegalArgumentException("Source account ID must be provided");
        }
        if (destinationAccountId == null) {
            throw new IllegalArgumentException("Destination account ID must be provided");
        }
        if (requesterId == null) {
            throw new IllegalArgumentException("Requester ID must be provided");
        }
        // equals는 두 UUID의 값이 같은지 비교합니다. 같은 계좌로 보내는 요청은 거부합니다.
        // 위에서 null을 걸러 두었기 때문에 안전하게 equals를 호출할 수 있습니다.
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }
        // 이체 금액은 최소 1원이어야 합니다. 아직 별도의 1회 이체 한도는 없습니다.
        // 잔액은 여기서 검사하지 않습니다. 요청 후 실행 전까지 다른 거래로 잔액이 바뀔 수 있기 때문입니다.
        if (amountWon <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        // this.sourceAccountId는 이 객체가 보관할 칸, 오른쪽 sourceAccountId는 밖에서 받은 값입니다.
        // 검사를 모두 통과한 값만 각 칸에 넣습니다.
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amountWon = amountWon;
        this.requesterId = requesterId;
        // 새 요청은 항상 REQUESTED입니다. 호출자가 "이미 승인된 요청"을 만들 수 없게 여기서 정합니다.
        this.status = TransferStatus.REQUESTED;
    }

    // 객체에게 "승인해 줘"라고 요청하는 메서드입니다.
    // 상태를 마음대로 덮어쓰는 setter 대신 이렇게 업무 이름의 메서드를 두어 검사를 꼭 거치게 합니다.
    public void approve(UUID reviewerId) {
        review(reviewerId, TransferStatus.APPROVED);
    }

    // 반려도 결재자 검사 과정은 같으므로 review를 같이 쓰고, 목표 상태만 REJECTED로 전달합니다.
    public void reject(UUID reviewerId) {
        review(reviewerId, TransferStatus.REJECTED);
    }

    // 승인·반려의 공통 순서: 결재자 ID 확인 → 자기 결재 금지 → 상태 순서 확인 → 사람·시각 기록.
    // 회사 소속은 DB 조회가 필요하므로 이 메서드를 부르기 전에 Service에서 검사합니다.
    private void review(UUID reviewerId, TransferStatus next) {
        if (reviewerId == null) {
            throw new IllegalArgumentException("Reviewer ID must be provided");
        }
        // 요청자와 결재자가 같으면 승인뿐 아니라 반려도 금지합니다. 다른 사람이 검토하도록 하는 규칙입니다.
        if (requesterId.equals(reviewerId)) {
            throw new TransferSelfReviewException();
        }
        // 현재 상태에서 목표 상태로 갈 수 있는지 먼저 검사합니다.
        // 예: REJECTED에서 APPROVED로 갈 수 없으면 예외로 멈추므로 아래 사람·시각도 바뀌지 않습니다.
        status = status.transitionTo(next);
        this.reviewerId = reviewerId;
        // Instant는 한 시점을 나타냅니다. now()로 현재 시각을 기록하며, 화면의 시간대 표시는 별도입니다.
        this.reviewedAt = Instant.now();
    }

    // 모의 출금·입금이 성공한 뒤 실행 서비스가 호출합니다. 이 메서드 자체는 돈을 움직이지 않습니다.
    // 출금·입금과 이 완료 기록을 같은 트랜잭션으로 저장해야 일부만 성공하는 일을 막을 수 있습니다.
    // public이 없으므로 같은 패키지의 코드에서 사용할 수 있습니다.
    void complete(UUID actorId) {
        if (actorId == null) {
            throw new IllegalArgumentException("Executor ID must be provided");
        }
        status = status.transitionTo(TransferStatus.COMPLETED);
        executedBy = actorId;
        completedAt = Instant.now();
    }

    // 아래 getter들은 객체 안에 보관된 값을 읽어서 돌려줍니다.
    // 이 객체의 getter를 부르는 것 자체가 별도의 DB 조회 명령은 아닙니다.
    // 값을 임의로 고치는 setter는 두지 않아 상태 변경은 위 업무 메서드를 거치게 합니다.
    public Instant getCompletedAt() { return completedAt; }
    public UUID getExecutedBy() { return executedBy; }

    public UUID getReviewerId() { return reviewerId; }
    public Instant getReviewedAt() { return reviewedAt; }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public long getAmountWon() { return amountWon; }
    public UUID getRequesterId() { return requesterId; }
    public TransferStatus getStatus() { return status; }
}
