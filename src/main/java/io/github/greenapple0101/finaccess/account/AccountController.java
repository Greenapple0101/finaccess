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

// [만든 순서 2] 준비된 Service 앞에 HTTP 입구를 붙입니다.
// 회사별 계좌를 표현하는 경로이며, companyId는 URL로 받습니다.
// 현재 인증 전의 로컬 학습용 API입니다. 실제 권한 통제는 아직 없습니다.
@RestController
@RequestMapping("/api/companies/{companyId}/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // POST는 새 계좌 생성을 요청합니다. 별도 JSON 본문은 필요 없습니다.
    // 초기 잔액을 입력받지 않고 서버의 Account 생성자가 0원으로 정합니다.
    // 정상 처리 결과는 HTTP 201 Created와 JSON으로 반환합니다.
    @PostMapping
    public ResponseEntity<AccountService.OpenedAccount> open(@PathVariable UUID companyId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.open(companyId));
    }

    // CompanyController에 선언한 예외 처리기는 그 Controller에만 적용됩니다.
    // 따라서 이 Controller에서도 회사 없음 예외를 404로 변환합니다.
    // 향후 공통 처리 항목이 늘면 전역 예외 처리기로 정리할 수 있습니다.
    @ExceptionHandler(CompanyNotFoundException.class)
    public ProblemDetail handleCompanyNotFound(CompanyNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
