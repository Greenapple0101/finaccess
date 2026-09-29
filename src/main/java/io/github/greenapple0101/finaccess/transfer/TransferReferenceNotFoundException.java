// [이 파일은 무엇을 하나요?]
// 처리에 필요한 이체 요청·계좌·사용자를 DB에서 찾지 못했다는 오류입니다.
// 예: 출금 계좌 ID로 조회했는데 없다면 Service가 이 예외를 발생시킵니다.
// 흐름: 조회 실패 → 이 예외 발생 → Controller가 404 응답으로 변환.
// RuntimeException 계열이라 Spring 트랜잭션 밖으로 전달되면 기본 설정상 DB 변경을 취소합니다.
package io.github.greenapple0101.finaccess.transfer;

import java.util.UUID;

public class TransferReferenceNotFoundException extends RuntimeException {
    // reference는 "무엇을 찾았는지", id는 "어떤 ID로 찾았는지"입니다.
    // 둘을 함께 남겨 계좌가 없는지, 사용자가 없는지 구별할 수 있게 합니다.
    public TransferReferenceNotFoundException(String reference, UUID id) {
        // 문자열을 +로 이어 오류 설명을 만들고, super로 부모 예외에 넘깁니다.
        // 여기서는 HTTP 응답을 만들지 않습니다. Controller가 오류를 응답으로 바꿉니다.
        super(reference + " not found: " + id);
    }
}
