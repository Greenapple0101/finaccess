// [이 파일의 역할: 출금액보다 잔액이 적은 경우를 표현]
// RuntimeException을 상속하는 사용자 정의 예외입니다.
// 예외는 오류 상황을 값처럼 이름 붙여 호출자에게 전달하는 방법입니다.
// throw new 예외(...)가 실행되면 아래 정상 코드는 중단되고 예외 처리 위치를 찾아 전달됩니다.
// super(...)는 부모 클래스의 생성자를 호출하여 메시지를 보관합니다.
// 예외 클래스 자체가 HTTP 응답을 보내거나 DB를 롤백하는 것은 아닙니다.
// Spring 트랜잭션 프록시나 Controller 예외 처리기가 이 예외를 보고 각 역할을 수행합니다.
// 회사 없음은 현재 Controller에서 404로 변환하며 잔액 부족은 아직 HTTP API에 연결하지 않았습니다.

// package는 이 파일의 소속 주소입니다. 계층별 호출 순서를 나타내는 문법은 아닙니다.
// 아래 import는 다른 패키지의 타입을 짧은 이름으로 쓰기 위한 선언이며, 실행이나 객체 생성이 아닙니다.
package io.github.greenapple0101.finaccess.account;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException() {
        super("Insufficient account balance");
    }
}
