package io.github.greenapple0101.finaccess.transfer;

import java.util.UUID;

// 요청에 필요한 계좌 또는 사용자가 없다는 뜻의 업무 예외입니다.
// RuntimeException을 상속하므로 기본 Spring 트랜잭션 롤백 대상입니다.
// HTTP 응답을 직접 만들지는 않습니다. API 단계에서 예외를 응답으로 변환합니다.
public class TransferReferenceNotFoundException extends RuntimeException {
    public TransferReferenceNotFoundException(String reference, UUID id) {
        // super는 부모 생성자를 호출해 오류 메시지를 전달합니다.
        super(reference + " not found: " + id);
    }
}
