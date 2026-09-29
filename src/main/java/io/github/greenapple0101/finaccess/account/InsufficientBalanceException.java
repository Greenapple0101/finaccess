// [잔액이 부족해 출금할 수 없다는 신호]
// 예: 잔액이 1,000원인데 1,200원을 출금하려 하면 Account.withdraw가 이 예외를 보냅니다.
// 잔액을 빼기 전에 발생하므로 그 계좌 객체의 잔액은 줄어들지 않습니다.
// 이체 중 예외가 밖으로 전달되면 Spring이 해당 트랜잭션의 DB 변경을 되돌리고,
// TransferController는 HTTP 409(현재 상태와 요청이 맞지 않음) 응답으로 바꿉니다.
// 이 예외 클래스 자체는 DB 복구나 HTTP 응답을 실행하지 않고 문제의 종류와 설명만 전달합니다.

package io.github.greenapple0101.finaccess.account;

// RuntimeException의 기본 예외 기능을 물려받아 "잔액 부족"이라는 구체적인 이름을 붙입니다.
public class InsufficientBalanceException extends RuntimeException {
    // super는 부모 생성자에 오류 설명을 전달합니다. 나중에 getMessage()로 이 설명을 읽습니다.
    public InsufficientBalanceException() {
        super("Insufficient account balance");
    }
}
