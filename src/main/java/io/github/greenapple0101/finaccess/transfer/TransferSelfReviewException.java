// [이 파일은 무엇을 하나요?]
// 자기가 만든 이체 요청을 자기가 승인하거나 반려하려 할 때 사용하는 오류입니다.
// 예: 요청자 ID와 결재자 ID가 같으면 TransferRequest가 이 예외를 발생시킵니다.
// 흐름: 자기 결재 확인 → 이 예외 발생 → 트랜잭션 취소 → Controller가 403 응답으로 변환.
// 다른 사람이 확인하도록 분리한 규칙입니다. 결재자의 회사·역할 검사는 별개입니다.
package io.github.greenapple0101.finaccess.transfer;

public class TransferSelfReviewException extends RuntimeException {
    public TransferSelfReviewException() {
        // 부모 RuntimeException에 이유를 전달합니다. 검사를 통과하지 못했으므로 상태 변경은 진행하지 않습니다.
        super("Requester cannot review their own transfer");
    }
}
