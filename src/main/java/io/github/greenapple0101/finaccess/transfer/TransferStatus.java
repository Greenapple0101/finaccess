// [이 파일의 역할]
// 이체 업무에는 순서가 있습니다. 요청한 뒤 승인 또는 반려하고, 승인된 건만 완료합니다.
// 이 파일은 그 상태 이름과 허용되는 이동 순서를 정의합니다. 상태 이동을 '전이'라고 부릅니다.
// 아직 이체 한 건을 담는 Entity나 HTTP API가 아닙니다. DB 저장과 계좌 입출금도 하지 않습니다.
// 먼저 가장 작은 업무 규칙을 만들고, 다음 단계에서 이체 객체가 이 규칙을 사용하게 합니다.
package io.github.greenapple0101.finaccess.transfer;

// enum(열거형)은 사용할 수 있는 값을 미리 정해 놓은 Java 타입입니다.
// 문자열 "approved"를 직접 쓰면 오타가 실행 중에 발견될 수 있지만,
// TransferStatus.APPROVED처럼 쓰면 존재하지 않는 이름은 컴파일 단계에서 발견됩니다.
// public은 다른 패키지에서도 이 타입을 사용할 수 있다는 뜻입니다.
// Spring 어노테이션은 없습니다. 이 규칙은 Spring이나 DB 없이도 실행할 수 있습니다.
public enum TransferStatus {
    // 아래 네 개가 이 타입의 값입니다. new TransferStatus()로 직접 만들지 않습니다.
    REQUESTED, // 이체 요청 접수: 아직 승인되지 않았고 돈도 이동하지 않은 상태.
    APPROVED,  // 승인됨: 실행을 허용받았다는 의미이며, 이체 완료와는 다릅니다.
    REJECTED,  // 반려됨: 이 요청은 종료됩니다. 다시 요청하려면 별도 요청을 만들어야 합니다.
    COMPLETED; // 완료됨: 향후 실제 모의 입출금이 성공한 뒤에만 기록해야 하는 상태.
    // 마지막 값 뒤의 세미콜론은 값 목록을 끝내고 아래에 메서드를 선언하기 위해 붙입니다.

    // [메서드 선언 읽기]
    // public: 외부에서 호출 가능 / TransferStatus: 돌려줄 값의 타입.
    // transitionTo: 메서드 이름 / 괄호 안 next: 호출자가 전달한 목표 상태.
    // 예: TransferStatus.REQUESTED.transitionTo(TransferStatus.APPROVED)
    // 호출 대상 REQUESTED가 현재 상태이고, 인자 APPROVED가 목표 상태입니다.
    //
    // 중요: enum 값 자체는 바뀌지 않습니다. 검사에 통과한 next를 반환할 뿐입니다.
    // 나중에 이체 객체가 status = status.transitionTo(next)처럼 결과를 저장해야 합니다.
    // 이 메서드는 '누가 승인하는가'를 모릅니다. 자기 승인 금지·회사 권한은 별도 검사입니다.
    public TransferStatus transitionTo(TransferStatus next) {
        // null은 목표 상태를 전달하지 않았다는 뜻입니다. 정상적인 상태 값이 아닙니다.
        // throw new ...는 예외를 만들어 호출한 쪽으로 알리고 현재 메서드 실행을 중단합니다.
        if (next == null) {
            throw new IllegalArgumentException("Target status must be provided");
        }

        // this는 이 메서드를 호출한 현재 enum 값입니다.
        // switch 식은 현재 값에 맞는 case를 골라 결과를 만듭니다.
        // boolean은 true/false만 담는 타입이며 allowed는 이동 허용 여부입니다.
        // enum 값은 ==로 비교합니다. ||는 둘 중 하나라도 참이면 참이라는 뜻입니다.
        boolean allowed = switch (this) {
            case REQUESTED -> next == APPROVED || next == REJECTED;
            case APPROVED -> next == COMPLETED;
            // 쉼표로 두 상태를 묶었습니다. 둘 다 끝난 상태이므로 어떤 이동도 허용하지 않습니다.
            case REJECTED, COMPLETED -> false;
        };

        // !는 참/거짓을 뒤집습니다. 허용되지 않았을 때만 이 블록을 실행합니다.
        // 같은 상태로 다시 이동하는 것도 거부합니다. 중복 요청 처리는 향후 별도 기능입니다.
        if (!allowed) {
            // +는 여기서 문자열을 이어 붙입니다. 현재 상태와 목표 상태를 오류에 남깁니다.
            throw new IllegalStateException("Cannot transition from " + this + " to " + next);
        }
        // return은 결과를 돌려주고 메서드를 종료합니다. DB나 잔액이 바뀌는 명령이 아닙니다.
        return next;
    }
}
