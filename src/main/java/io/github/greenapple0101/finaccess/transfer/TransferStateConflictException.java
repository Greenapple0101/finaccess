package io.github.greenapple0101.finaccess.transfer;

// 현재 상태에서는 요청한 행동을 할 수 없다는 업무 예외입니다.
// IllegalStateException을 상속하지만 HTTP에서는 이 구체적인 예외만 409로 변환합니다.
public class TransferStateConflictException extends IllegalStateException {
    public TransferStateConflictException(String message) {
        super(message);
    }
}
