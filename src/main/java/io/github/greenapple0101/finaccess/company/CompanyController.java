// [이 파일의 역할: 회사 업무를 HTTP와 연결하는 입구]
// 등록 요청: POST /api/companies, JSON {"name":"핀액세스"}.
// 조회 요청: GET /api/companies/실제UUID.
// 이 클래스는 HTTP 경로·입력·상태 코드·오류 응답을 다룹니다.
// 회사를 저장할 작업 자체는 CompanyService에 맡깁니다.
//
// 만든 순서: SQL 테이블 → Company → Repository → Service → 이 Controller.
// 실행 순서: 외부 요청 → 이 Controller → Service → Repository → DB.
// 아래 record는 데이터 모양을 정의한 타입이며 새로운 실행 계층이 아닙니다.
// 현재는 로컬 실습용 API입니다. 회사 소속·역할에 따른 접근 검사는 아직 없습니다.

// package는 이 파일의 소속 주소입니다. 계층별 호출 순서를 나타내는 문법은 아닙니다.
// 아래 import는 다른 패키지의 타입을 짧은 이름으로 쓰기 위한 선언이며, 실행이나 객체 생성이 아닙니다.
package io.github.greenapple0101.finaccess.company;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @Controller + @ResponseBody의 의미를 함께 가집니다.
// Spring의 웹 컴포넌트로 등록하고, 반환값을 화면 이름이 아니라 응답 본문으로 처리합니다.
// 객체 반환은 현재 구성에서 Jackson이 JSON으로 변환합니다.
// 어노테이션 자체가 실행문은 아니며 Spring MVC가 이 정보를 읽어 동작을 연결합니다.
@RestController
// 클래스에 붙었으므로 이 Controller의 공통 경로입니다.
// 메서드의 Mapping 경로와 합쳐 최종 URL이 결정됩니다. 폴더 이름으로 URL이 결정되지는 않습니다.
@RequestMapping("/api/companies")
public class CompanyController {

    // private: 외부 코드가 이 필드에 직접 접근하지 못하게 합니다.
    // final: 생성 후 다른 Service 참조를 다시 대입하지 않도록 합니다.
    // 필드는 필요한 객체를 보관하는 자리입니다. new로 직접 생성하지 않고 생성자에서 전달받습니다.
    private final CompanyService companyService;

    // 이것은 일반 메서드가 아니라 클래스 이름과 같은 생성자입니다. 반환 타입이 없습니다.
    // Spring이 Controller를 만들 때 필요한 Service 빈을 찾아 인자로 전달합니다(생성자 주입).
    // 생성자가 하나라 @Autowired를 붙이지 않아도 주입에 사용됩니다.
    // this.필드는 현재 객체의 멤버, 오른쪽 매개변수는 외부에서 전달받은 값입니다.
    // Service가 없다면 Spring이 이 Controller를 구성하지 못해 앱 시작이 실패할 수 있습니다.
    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    // POST 요청을 처리할 메서드입니다. 경로가 없으므로 클래스의 공통 경로를 그대로 사용합니다.
    // ResponseEntity<T>의 T는 응답 본문 타입이며, 상태 코드와 본문을 함께 지정합니다.
    // status(CREATED)가 응답 빌더를 만들고 body(...)가 본문을 붙인 응답 객체를 완성합니다.
    // 점(.)으로 이어지는 호출을 읽을 때 각 메서드가 반환한 객체에 다음 메서드를 호출한다고 보면 됩니다.
    @PostMapping
    // @RequestBody: 들어온 JSON을 RegisterCompanyRequest로 역직렬화합니다.
    // 역직렬화는 JSON → Java 객체, 직렬화는 Java 객체 → JSON입니다.
    // request.name()은 record가 제공하는 접근자이며 JSON의 name 값을 꺼냅니다.
    // companyService.register(...)가 먼저 실행되어야 그 결과를 body에 담을 수 있습니다.
    // 유효하지 않은 JSON은 이 메서드에 진입하기 전 Spring MVC에서 거부될 수 있습니다.
    public ResponseEntity<CompanyService.RegisteredCompany> register(@RequestBody RegisterCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.register(request.name()));
    }

    // GET은 조회를 위한 요청입니다. {id}는 바뀔 수 있는 경로 자리입니다.
    // @PathVariable UUID id는 그 문자열을 UUID로 바꿔 매개변수에 넣으라는 뜻입니다.
    // UUID 문자열 형식이 잘못되면 400, 형식은 맞지만 회사가 없으면 Service의 예외를 거쳐 404가 됩니다.
    @GetMapping("/{id}")
    public CompanyService.CompanyDetails findById(@PathVariable UUID id) {
        return companyService.findById(id);
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

    // Company 생성자의 이름 검증 실패를 400 Bad Request로 바꿉니다.
    // DB 장애나 모든 서버 오류를 400으로 바꾸는 범용 핸들러는 아닙니다.
    // 업무 예외가 늘어나면 더 구체적인 예외 타입과 공통 오류 정책을 설계할 수 있습니다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidCompany(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // record는 값을 담는 타입을 짧게 정의하는 Java 문법이며 Spring 전용 문법이 아닙니다.
    // 생성자, name() 접근자, equals/hashCode/toString 등을 컴파일러가 제공합니다.
    // 현재 타입은 Controller 안에 선언한 중첩 타입입니다. 별도 실행 단계나 서버가 아닙니다.
    // 요청에는 이름만 받으므로 DB 엔티티의 id·생성 시각을 외부 입력과 분리합니다.
    public record RegisterCompanyRequest(String name) {
    }
}
