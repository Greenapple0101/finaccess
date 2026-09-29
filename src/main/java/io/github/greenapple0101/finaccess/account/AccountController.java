// [계좌 개설 요청을 받는 HTTP 입구]
// POST /api/companies/{companyId}/accounts 요청을 받아 해당 회사의 계좌를 만듭니다.
// {companyId} 자리에는 실제 회사의 UUID를 넣습니다. 회사 ID는 JSON 본문이 아니라 주소에서 받습니다.
// 흐름: 주소에서 회사 ID 꺼내기 → AccountService.open 호출 → 결과를 JSON으로 응답.
//
// 성공하면 201과 계좌 ID·회사 ID·0원 잔액을 돌려줍니다. 회사가 없으면 404, ID 형식이 틀리면 400입니다.
// 요청 본문으로 잔액을 보내도 사용하지 않습니다. "본문을 보내면 무조건 거부"하도록 만든 것은 아닙니다.
// 같은 요청을 두 번 보내면 계좌도 두 개 생깁니다. 중복 요청 방지 기능은 아직 없습니다.
// 현재 계좌 개설 API는 회사 존재만 확인합니다. 로그인 및 소속 권한 검사는 없어 로컬 학습용입니다.

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

// Spring이 HTTP 요청을 받을 객체로 등록하고, 반환 데이터를 JSON 응답 본문으로 보내게 합니다.
@RestController
// 이 Controller가 받을 공통 주소입니다. 중괄호 부분만 회사 ID마다 바뀝니다.
@RequestMapping("/api/companies/{companyId}/accounts")
public class AccountController {
    // 계좌 개설 업무를 맡길 Service입니다. Controller가 직접 DB 저장 코드를 작성하지 않습니다.
    private final AccountService accountService;

    // Spring이 필요한 Service를 생성자로 전달해 줍니다. 이 방식이 "생성자 주입"입니다.
    // 전달받은 객체를 필드에 보관했다가 아래 open에서 사용합니다.
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // POST 요청을 아래 메서드에 연결합니다. @PathVariable은 주소의 companyId를 UUID로 바꾸어 받습니다.
    // 먼저 Service의 open으로 계좌를 만들고, 응답 본문(body)에 그 결과를 담습니다.
    // ResponseEntity는 상태 코드와 데이터를 함께 담는 응답이며 CREATED는 "생성 성공"인 HTTP 201입니다.
    @PostMapping
    public ResponseEntity<AccountService.OpenedAccount> open(@PathVariable UUID companyId) {
        AccountService.OpenedAccount openedAccount = accountService.open(companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(openedAccount);
    }

    // Service가 "회사를 찾지 못했다"는 예외를 보내면 이 메서드가 HTTP 오류로 바꿉니다.
    // ProblemDetail은 오류 상태와 설명을 담는 타입이고, NOT_FOUND는 HTTP 404입니다.
    // 이 예외 처리기는 이 Controller의 요청에만 적용됩니다.
    @ExceptionHandler(CompanyNotFoundException.class)
    public ProblemDetail handleCompanyNotFound(CompanyNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
