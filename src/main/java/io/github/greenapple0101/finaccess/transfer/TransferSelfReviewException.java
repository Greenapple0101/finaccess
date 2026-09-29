package io.github.greenapple0101.finaccess.transfer;

// 자기 요청의 승인과 반려를 모두 금지하는 결재자 분리 규칙입니다.
// HTTP 변환은 Controller가 하고 RuntimeException은 트랜잭션 롤백 대상입니다.
public class TransferSelfReviewException extends RuntimeException {
    public TransferSelfReviewException() {
        super("Requester cannot review their own transfer");
    }
}
