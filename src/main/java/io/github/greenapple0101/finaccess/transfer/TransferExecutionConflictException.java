// [이 파일은 무엇을 하나요?]
// 현재 계좌 값으로 이체를 끝낼 수 없다는 오류를 전달합니다.
// 예: 입금 후 잔액이 long의 최대 범위를 넘으면 실행 서비스가 이 예외를 발생시킵니다.
// 흐름: 입금 오류 → 이 예외로 바꿔 전달 → 트랜잭션 취소 → Controller가 409 응답으로 변환.
// 이미 출금 SQL을 보냈더라도 아직 확정 전이라면 함께 취소됩니다.
package io.github.greenapple0101.finaccess.transfer;

public class TransferExecutionConflictException extends RuntimeException {
    // message에는 업무상 실패 이유, cause에는 원래 발생한 오류를 받습니다.
    // 원인도 보관해야 나중에 문제를 추적할 때 처음 무슨 일이 생겼는지 알 수 있습니다.
    public TransferExecutionConflictException(String message, Throwable cause) {
        // 부모 RuntimeException에 설명과 원인을 전달합니다.
        super(message, cause);
    }
}
