// [이 파일은 무엇을 하나요?]
// 이체의 네 가지 상태 이름과, 어떤 상태에서 다음 상태로 갈 수 있는지 정합니다.
// 순서: REQUESTED → APPROVED 또는 REJECTED, APPROVED → COMPLETED. 종료된 상태에서는 더 이동하지 않습니다.
// TransferRequest가 승인·반려·완료할 때 이 규칙을 사용합니다. 상태 변경 순서를 '전이'라고도 부릅니다.
// 이 파일은 순서만 검사합니다. 사용자 권한 검사, DB 저장, 계좌의 돈 이동은 하지 않습니다.
package io.github.greenapple0101.finaccess.transfer;

// enum은 쓸 수 있는 값을 아래 목록으로 한정한 타입입니다.
// 문자열을 직접 적으면 "APROVED"처럼 오타를 내기 쉽지만, enum에 없는 이름은 빌드할 때 잡힙니다.
// 아래 값은 미리 정해져 있으므로 new로 상태를 만들지 않습니다.
public enum TransferStatus {
    REQUESTED, // 이체 요청 접수: 아직 승인되지 않았고 돈도 이동하지 않은 상태.
    APPROVED,  // 승인됨: 실행을 허용받았다는 의미이며, 이체 완료와는 다릅니다.
    REJECTED,  // 반려됨: 이 요청은 종료됩니다. 다시 요청하려면 별도 요청을 만들어야 합니다.
    COMPLETED; // 완료됨: 모의 입출금과 같은 트랜잭션에서 성공 시 기록하는 상태.

    // 예: REQUESTED.transitionTo(APPROVED)는 "요청됨에서 승인됨으로 갈 수 있니?"라는 뜻입니다.
    // 호출한 값이 현재 상태, 괄호 안 next가 가려는 상태입니다.
    // 이 메서드는 enum 자체를 바꾸지 않고 허용된 next를 돌려줍니다.
    // 그래서 요청 객체가 status = status.transitionTo(next)처럼 결과를 다시 저장해야 합니다.
    public TransferStatus transitionTo(TransferStatus next) {
        // 목표 상태를 빼먹었다면 여기서 중단합니다. null은 다섯 번째 상태가 아니라 값이 없다는 뜻입니다.
        if (next == null) {
            throw new IllegalArgumentException("Target status must be provided");
        }

        // this는 현재 상태입니다. switch는 그 값에 해당하는 case 한 줄을 골라 결과를 만듭니다.
        // allowed에는 true(허용) 또는 false(거부)가 들어갑니다.
        // ||는 "둘 중 하나라도 맞으면 허용"이라는 뜻입니다. enum 값은 ==로 같은지 비교합니다.
        boolean allowed = switch (this) {
            case REQUESTED -> next == APPROVED || next == REJECTED;
            case APPROVED -> next == COMPLETED;
            case REJECTED, COMPLETED -> false;
        };

        // !는 참과 거짓을 뒤집으므로 "허용되지 않았다면" 이 블록을 실행합니다.
        // 예: 승인 전 바로 완료하거나, 이미 승인한 요청을 다시 승인하면 오류입니다.
        // 같은 요청을 반복 호출해도 성공으로 돌려주는 멱등성 처리는 아직 별도 구현하지 않았습니다.
        if (!allowed) {
            throw new TransferStateConflictException("Cannot transition from " + this + " to " + next);
        }
        // 순서 검사를 통과한 목표 상태를 반환합니다. 누가 결재하는지는 이 메서드가 알지 못합니다.
        return next;
    }
}
