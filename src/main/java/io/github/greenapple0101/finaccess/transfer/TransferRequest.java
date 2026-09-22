// [이 파일의 역할: 이체 요청 한 건을 표현합니다]
// TransferStatus가 상태의 이름이라면, 이 클래스는 누가 어느 계좌에서 어디로 얼마를
// 보내려는지와 현재 상태를 함께 담는 상자입니다. HTTP 요청 본문용 DTO는 아닙니다.
// 이번에는 순수 Java 객체로 업무 규칙을 먼저 만듭니다. JPA 매핑과 DB 저장은 다음 단계입니다.
package io.github.greenapple0101.finaccess.transfer;

// UUID는 기존 계좌·사용자의 내부 ID에 쓰는 타입입니다.
// import는 짧은 이름으로 사용하도록 하는 선언이며 계좌를 조회하는 명령이 아닙니다.
import java.util.UUID;

// public은 다른 패키지에서도 사용할 수 있다는 뜻, class는 객체의 구조와 행동을 정의하는 문법입니다.
// 같은 클래스로 서로 다른 이체 요청 객체를 여러 개 만들 수 있습니다.
public class TransferRequest {
    // private은 외부에서 필드에 직접 접근하지 못하게 합니다.
    // final은 생성할 때 정한 값을 이후 다른 값으로 대입하지 못하게 합니다.
    // 승인 대상의 계좌나 금액이 중간에 바뀌면 안 되므로 요청 내용을 고정합니다.
    // 계좌 객체 전체 대신 ID를 보관합니다. ID만 있다고 계좌가 실제로 존재하는 것은 아닙니다.
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;
    private final long amountWon;
    private final UUID requesterId;

    // 현재 단계에는 상태 변경 기능을 공개하지 않습니다. 요청은 반드시 REQUESTED로 시작합니다.
    // 이후 승인 기능에서 상태 변경을 추가할 때 TransferStatus의 전이 규칙을 사용합니다.
    private final TransferStatus status;

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
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public long getAmountWon() { return amountWon; }
    public UUID getRequesterId() { return requesterId; }
    public TransferStatus getStatus() { return status; }
}
