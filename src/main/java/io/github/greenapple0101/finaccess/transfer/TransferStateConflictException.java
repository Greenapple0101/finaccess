// [이 파일은 무엇을 하나요?]
// 현재 상태에서는 그 행동을 할 수 없다는 오류입니다.
// 예: 반려된 요청을 승인하거나, 아직 승인되지 않은 요청을 실행하려 하면 발생합니다.
// 흐름: 상태 검사 실패 → 이 예외 발생 → Controller가 409 응답으로 변환.
// IllegalStateException도 RuntimeException 계열이어서 기본 트랜잭션 취소 대상입니다.
package io.github.greenapple0101.finaccess.transfer;

// 일반적인 "상태가 잘못됨" 오류에서 이체 업무의 상태 충돌만 구별하려고 별도 이름을 만들었습니다.
// Controller는 모든 IllegalStateException이 아니라 이 예외만 409로 처리합니다.
public class TransferStateConflictException extends IllegalStateException {
    public TransferStateConflictException(String message) {
        // 호출한 쪽에서 만든 구체적인 이유를 부모 예외에 전달합니다.
        super(message);
    }
}
