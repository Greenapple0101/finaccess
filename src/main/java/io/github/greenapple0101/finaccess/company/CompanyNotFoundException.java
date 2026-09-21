package io.github.greenapple0101.finaccess.company;

import java.util.UUID;

// 회사 조회 결과가 없다는 사실을 명시적으로 전달하는 사용자 정의 예외입니다.
// RuntimeException을 상속해 실행 중 업무 실패를 나타냅니다.
// 생성자의 super(...)는 부모 예외 클래스에 오류 메시지를 전달합니다.
// 404 응답으로 변환하는 일은 Controller가 맡습니다.
public class CompanyNotFoundException extends RuntimeException {

    public CompanyNotFoundException(UUID id) {
        super("Company not found: " + id);
    }
}
