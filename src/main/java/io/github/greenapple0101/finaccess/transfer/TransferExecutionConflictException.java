package io.github.greenapple0101.finaccess.transfer;

// 입금할 계좌의 잔액 범위를 초과하는 등 현재 데이터로 실행할 수 없는 경우입니다.
// 원인 예외(cause)를 보존하고 RuntimeException으로 전달하여 전체 트랜잭션을 롤백합니다.
public class TransferExecutionConflictException extends RuntimeException {
    public TransferExecutionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
