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

// [읽기 3] 회사 API의 입구입니다. HTTP 관련 처리를 담당합니다.
// 등록: POST /api/companies, 조회: GET /api/companies/{id}.
// DB에 직접 접근하지 않고 CompanyService에 업무를 맡깁니다.
// 현재는 로컬 학습 단계로, 로그인 및 회사 소속에 따른 접근 통제는 아직 없습니다.
@RestController
// 클래스에 붙은 RequestMapping은 메서드들의 공통 URL 앞부분을 지정합니다.
@RequestMapping("/api/companies")
public class CompanyController {

    // private은 이 클래스 내부에서 사용하는 필드라는 뜻입니다.
    // final은 생성자에서 한 번 연결한 참조를 다른 객체로 다시 대입하지 않겠다는 뜻입니다.
    // Service 객체의 내부 상태까지 불변으로 만드는 키워드는 아닙니다.
    private final CompanyService companyService;

    // 생성자: Spring이 CompanyService 빈을 찾아 인자로 전달합니다(생성자 주입).
    // this.companyService는 위 필드, 오른쪽 companyService는 전달받은 매개변수입니다.
    // 생성자가 하나라 @Autowired를 따로 붙이지 않아도 Spring이 사용할 수 있습니다.
    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    // 경로를 추가하지 않았으므로 POST /api/companies를 처리합니다.
    // @RequestBody는 JSON 본문을 RegisterCompanyRequest 객체로 변환합니다.
    // request.name()으로 이름을 꺼내 Service에 전달합니다.
    // ResponseEntity는 본문뿐 아니라 상태 코드도 지정하는 응답용 타입입니다.
    // CREATED는 201: 회사가 새로 생성되었다는 의미입니다.
    // Service의 트랜잭션이 정상 종료된 후 반환받은 결과가 응답 본문에 들어갑니다.
    @PostMapping
    public ResponseEntity<CompanyService.RegisteredCompany> register(@RequestBody RegisterCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.register(request.name()));
    }

    // 클래스의 경로와 합쳐 GET /api/companies/{id}가 됩니다.
    // @PathVariable은 URL의 id를 받으며 Spring이 문자열을 UUID로 변환합니다.
    // 변환 불가능한 문자열이면 Service를 호출하기 전에 400으로 거부됩니다.
    @GetMapping("/{id}")
    public CompanyService.CompanyDetails findById(@PathVariable UUID id) {
        return companyService.findById(id);
    }

    // Service에서 회사 없음 예외가 전달되면 이 메서드가 HTTP 응답으로 바꿉니다.
    // ProblemDetail은 status, title, detail 등으로 오류를 표현하는 타입입니다.
    // NOT_FOUND는 404: 요청 형식은 맞지만 해당 회사를 찾지 못했다는 뜻입니다.
    @ExceptionHandler(CompanyNotFoundException.class)
    public ProblemDetail handleCompanyNotFound(CompanyNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    // 회사 생성자의 이름 검사에서 발생한 예외를 400 Bad Request로 변환합니다.
    // 이 핸들러는 이 Controller의 요청 처리 범위에 적용되며 전역 핸들러는 아닙니다.
    // 모든 서버 오류를 400으로 바꾸는 것은 아닙니다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidCompany(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // 요청 본문용 DTO(Data Transfer Object): 외부에서 입력받을 데이터 모양입니다.
    // DB 엔티티와 분리하여 클라이언트가 id나 생성 시각을 마음대로 지정하지 않게 합니다.
    public record RegisterCompanyRequest(String name) {
    }
}
