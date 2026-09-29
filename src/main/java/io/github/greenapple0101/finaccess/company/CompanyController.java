// [회사 등록·조회 요청을 받는 HTTP 입구]
// 클라이언트가 보내는 요청을 받아 Service에 일을 맡기고, 결과를 HTTP 응답으로 돌려줍니다.
// 클라이언트는 요청을 보내는 쪽입니다. 브라우저·다른 프로그램·curl 등이 될 수 있습니다.
//
// 등록: POST /api/companies에 {"name":"핀액세스"}를 보내면 새 회사를 만듭니다.
// 조회: GET /api/companies/{id}에 실제 회사 ID를 넣으면 회사 정보를 읽습니다.
// 실행 순서: HTTP 요청 → Controller → CompanyService → CompanyRepository → DB.
// 응답은 반대 방향으로 돌아옵니다. 폴더가 여러 겹이라고 여러 서버가 생기는 것은 아닙니다.
//
// 이 파일은 주소·입력·응답을 담당하고, 저장 순서와 업무 처리는 Service가 담당합니다.
// 등록은 관리자만 가능하고, 조회는 자기 회사만 가능합니다. 관리자는 운영용 회사 조회를 허용합니다.

package io.github.greenapple0101.finaccess.company;

import io.github.greenapple0101.finaccess.security.CurrentAppUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
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

// Spring이 이 클래스를 HTTP 요청 처리 담당으로 등록하게 합니다.
// 메서드가 돌려준 데이터를 JSON으로 바꾸어 응답 본문에 넣습니다.
// JSON은 {"name":"핀액세스"}처럼 항목 이름과 값을 짝지은 데이터 표현입니다.
@RestController
// 이 Controller의 공통 주소입니다. 아래 메서드의 주소와 합쳐서 사용합니다.
// 폴더나 클래스 이름이 아니라 이 어노테이션의 값으로 요청 주소를 정합니다.
@RequestMapping("/api/companies")
public class CompanyController {

    // 회사 업무를 맡길 Service를 보관하는 자리입니다.
    // private은 이 클래스 안에서 사용한다는 뜻이고, final은 다른 Service로 바꿔 끼우지 않겠다는 뜻입니다.
    private final CompanyService companyService;
    private final CurrentAppUser currentAppUser;

    // Spring이 Controller를 만들 때 필요한 CompanyService를 여기로 전달합니다.
    // 필요한 객체를 밖에서 받아 연결하는 방식을 "의존성 주입"이라고 부릅니다.
    // 생성자가 하나라 @Autowired를 따로 쓰지 않아도 Spring이 이 생성자를 사용합니다.
    // this.companyService에 받은 객체를 보관해 두면 아래 메서드에서도 사용할 수 있습니다.
    public CompanyController(CompanyService companyService, CurrentAppUser currentAppUser) {
        this.companyService = companyService;
        this.currentAppUser = currentAppUser;
    }

    // POST는 새 회사 등록 요청에 사용합니다. 주소를 더 붙이지 않아 /api/companies를 그대로 씁니다.
    // @RequestBody는 JSON 본문을 아래 RegisterCompanyRequest 모양의 객체로 바꾸어 받게 합니다.
    // 예: {"name":"핀액세스"} → request.name()으로 "핀액세스" 꺼내기.
    // JSON 문법 자체가 틀리면 Spring이 이 메서드를 실행하기 전에 400 오류를 보낼 수 있습니다.
    @PostMapping
    public ResponseEntity<CompanyService.RegisteredCompany> register(@RequestBody RegisterCompanyRequest request) {
        // 먼저 Service의 register를 실행하고, 돌려받은 회사 정보를 응답 본문(body)에 담습니다.
        // ResponseEntity는 상태 코드와 본문을 함께 담는 응답입니다. CREATED는 HTTP 201, 즉 생성 성공입니다.
        // <CompanyService.RegisteredCompany>는 본문으로 담을 데이터의 종류를 나타냅니다.
        CompanyService.RegisteredCompany registeredCompany = companyService.register(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredCompany);
    }

    // GET은 이미 있는 회사 조회에 사용합니다. {id}에는 회사마다 다른 ID가 들어갑니다.
    // @PathVariable은 주소의 id 부분을 꺼내 UUID 타입으로 바꾸어 줍니다.
    // UUID 모양이 잘못되면 400, 모양은 맞지만 회사가 없으면 아래 오류 처리기를 거쳐 404입니다.
    @GetMapping("/{id}")
    public CompanyService.CompanyDetails findById(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        // admin은 회사 등록·운영을 담당하므로 여러 회사를 조회할 수 있게 명시적으로 허용합니다.
        // 일반 사용자는 토큰에 회사 ID를 직접 적어 보내는 대신 우리 DB의 소속을 사용합니다.
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_admin"));
        if (!isAdmin) {
            UUID companyId = currentAppUser.resolve(authentication.getToken()).companyId();
            if (!companyId.equals(id)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot read another company");
            }
        }
        return companyService.findById(id);
    }

    // 처리 중 "회사가 없다"는 예외가 올라오면 Spring이 이 메서드로 연결합니다.
    // ProblemDetail은 오류 상태·설명을 담는 응답이고, NOT_FOUND는 HTTP 404입니다.
    // getMessage()로 예외 안의 설명을 꺼내 응답에 넣습니다. 이 처리기는 이 Controller에만 적용됩니다.
    @ExceptionHandler(CompanyNotFoundException.class)
    public ProblemDetail handleCompanyNotFound(CompanyNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    // 회사 이름이 비어 있거나 너무 길면 생성자에서 IllegalArgumentException이 발생합니다.
    // 그 신호를 받아 BAD_REQUEST(HTTP 400), 즉 입력이 잘못되었다는 응답으로 바꿉니다.
    // DB 연결 실패 등 모든 오류가 여기로 들어오는 것은 아닙니다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidCompany(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // record는 함께 전달할 값을 묶는 Java 문법입니다. 여기서는 회사 이름만 받습니다.
    // Java가 값을 받는 생성자와 name()처럼 값을 꺼내는 메서드를 자동으로 만들어 줍니다.
    // Controller 안에 적었지만 별도의 실행 단계가 아닙니다. 요청 데이터의 모양을 정의한 것입니다.
    // 회사 ID와 생성 시각은 서버가 정하므로 이 요청 입력에는 포함하지 않았습니다.
    public record RegisterCompanyRequest(String name) {
    }
}
