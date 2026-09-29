// [회사를 찾지 못했다는 신호]
// 예: 회사 ID로 조회했는데 DB에 해당 회사가 없으면 이 예외를 보냅니다.
// 처리 순서: Service에서 throw → 현재 작업 중단 → Controller가 HTTP 404 응답으로 변환.
// 이 클래스는 오류의 이름과 설명만 담습니다. 스스로 HTTP 응답이나 DB 복구를 실행하지 않습니다.

package io.github.greenapple0101.finaccess.company;

import java.util.UUID;

// extends는 기존 타입을 바탕으로 새 타입을 만든다는 뜻입니다.
// RuntimeException의 예외 기능을 물려받아 "회사 없음"이라는 구체적인 상황에 이름을 붙입니다.
public class CompanyNotFoundException extends RuntimeException {

    // super는 부모인 RuntimeException의 생성자를 호출합니다.
    // 찾지 못한 ID를 메시지에 넣어 어느 회사 조회가 실패했는지 알 수 있게 합니다.
    public CompanyNotFoundException(UUID id) {
        super("Company not found: " + id);
    }
}
