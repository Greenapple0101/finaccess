package io.github.greenapple0101.finaccess.account;

// 잔액보다 큰 금액을 출금하려는 업무 실패를 구분하는 예외입니다.
// 아직 입출금 API가 없으므로 HTTP 상태 코드로 변환하는 처리기는 추가하지 않습니다.
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException() {
        super("Insufficient account balance");
    }
}
