// [이 파일의 역할: 회사 소유 계좌를 개설하는 HTTP 입구]
// POST /api/companies/{companyId}/accounts를 AccountService.open에 연결합니다.
// companyId는 URL에서 받습니다. 요청 JSON 본문을 받을 매개변수는 없습니다.
// 잔액을 외부 입력으로 받지 않아 Account의 규칙대로 0원으로 개설합니다.
// 추가 본문을 보내면 현재 구현은 사용하지 않습니다. 거부한다고 구현한 것은 아닙니다.
//
// 성공: 201과 계좌 id·companyId·balanceWon. 회사 없음: 404. 잘못된 UUID: 400.
// 같은 회사로 다시 요청하면 별도 계좌가 생깁니다. 아직 개설 멱등키는 없습니다.
// 회사 존재 확인은 권한 검사가 아닙니다. 회사 소속 권한 검증은 이후 단계입니다.

// package는 이 파일의 소속 주소입니다. 계층별 호출 순서를 나타내는 문법은 아닙니다.
// 아래 import는 다른 패키지의 타입을 짧은 이름으로 쓰기 위한 선언이며, 실행이나 객체 생성이 아닙니다.
package io.github.greenapple0101.finaccess.account;

import io.github.greenapple0101.finaccess.company.CompanyNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// @Controller + @ResponseBody의 의미를 함께 가집니다.
// Spring의 웹 컴포넌트로 등록하고, 반환값을 화면 이름이 아니라 응답 본문으로 처리합니다.
// 객체 반환은 현재 구성에서 Jackson이 JSON으로 변환합니다.
// 어노테이션 자체가 실행문은 아니며 Spring MVC가 이 정보를 읽어 동작을 연결합니다.
@RestController
// 클래스에 붙었으므로 이 Controller의 공통 경로입니다.
// 메서드의 Mapping 경로와 합쳐 최종 URL이 결정됩니다. 폴더 이름으로 URL이 결정되지는 않습니다.
@RequestMapping("/api/companies/{companyId}/accounts")
public class AccountController {
    // private: 외부 코드가 이 필드에 직접 접근하지 못하게 합니다.
    // final: 생성 후 다른 Service 참조를 다시 대입하지 않도록 합니다.
    // 필드는 필요한 객체를 보관하는 자리입니다. new로 직접 생성하지 않고 생성자에서 전달받습니다.
    private final AccountService accountService;

    // 이것은 일반 메서드가 아니라 클래스 이름과 같은 생성자입니다. 반환 타입이 없습니다.
    // Spring이 Controller를 만들 때 필요한 Service 빈을 찾아 인자로 전달합니다(생성자 주입).
    // 생성자가 하나라 @Autowired를 붙이지 않아도 주입에 사용됩니다.
    // this.필드는 현재 객체의 멤버, 오른쪽 매개변수는 외부에서 전달받은 값입니다.
    // Service가 없다면 Spring이 이 Controller를 구성하지 못해 앱 시작이 실패할 수 있습니다.
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // POST 요청을 처리할 메서드입니다. 경로가 없으므로 클래스의 공통 경로를 그대로 사용합니다.
    // ResponseEntity<T>의 T는 응답 본문 타입이며, 상태 코드와 본문을 함께 지정합니다.
    // status(CREATED)가 응답 빌더를 만들고 body(...)가 본문을 붙인 응답 객체를 완성합니다.
    // 점(.)으로 이어지는 호출을 읽을 때 각 메서드가 반환한 객체에 다음 메서드를 호출한다고 보면 됩니다.
    @PostMapping
    public ResponseEntity<AccountService.OpenedAccount> open(@PathVariable UUID companyId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.open(companyId));
    }

    // 아래 CompanyNotFoundException.class는 예외 클래스의 타입 정보입니다.
    // 요청 처리 중 이 예외가 전달되면 Spring MVC가 이 메서드를 선택합니다.
    // exception은 발생한 예외 객체이고 getMessage()는 그 안에 담긴 설명을 읽습니다.
    // ProblemDetail은 오류 상태와 설명을 담는 응답 타입입니다. 이 설정에서는 404를 반환합니다.
    // 이 핸들러는 현재 Controller에만 적용됩니다. 다른 Controller로 자동 공유되지 않습니다.
    @ExceptionHandler(CompanyNotFoundException.class)
    public ProblemDetail handleCompanyNotFound(CompanyNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
