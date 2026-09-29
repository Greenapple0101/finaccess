// [이 파일은 무엇을 하나요?]
// 외부에서 보낸 이체 요청을 받는 창구입니다. 주소에 따라 요청 생성·승인·반려·실행을 구분합니다.
// 실행 순서: HTTP 요청 → JSON을 Java 값으로 읽기 → Service에 일 맡기기 → 결과를 JSON으로 보내기.
// 예를 들어 POST /api/transfers는 "1,000원을 보내 주세요"라는 요청을 만들 뿐, 바로 출금하지 않습니다.
// Spring Security가 토큰과 역할을 먼저 검사합니다. 요청자·처리자는 검증된 토큰의 iss + sub로 찾습니다.
package io.github.greenapple0101.finaccess.transfer;

import java.util.UUID;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.github.greenapple0101.finaccess.security.CurrentAppUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import io.github.greenapple0101.finaccess.account.InsufficientBalanceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // 반환 객체를 응답 본문(JSON)으로 변환하는 Controller입니다.
@RequestMapping("/api/transfers") // 이 클래스에서 처리할 공통 주소입니다.
public class TransferController {
    // transferRequestService는 새 요청을 만들고, transferWorkflowService는 만들어진 요청을 처리합니다.
    // Controller는 잔액을 직접 고치지 않고 이 두 서비스에 필요한 값을 전달합니다.
    private final TransferRequestService transferRequestService;
    private final TransferWorkflowService transferWorkflowService;
    private final CurrentAppUser currentAppUser;

    // Spring이 미리 준비한 서비스 객체를 아래 생성자로 넘겨줍니다.
    // 필요한 도구를 직접 만드는 대신 밖에서 받는 방식을 '의존성 주입'이라고 합니다.
    public TransferController(TransferRequestService transferRequestService, TransferWorkflowService transferWorkflowService, CurrentAppUser currentAppUser) {
        this.transferRequestService = transferRequestService;
        this.transferWorkflowService = transferWorkflowService;
        this.currentAppUser = currentAppUser;
    }

    // @PostMapping은 POST 방식의 요청과 이 메서드를 연결합니다.
    // @RequestBody는 받은 JSON을 CreateTransfer 모양의 Java 값으로 읽게 합니다.
    // 예: JSON의 amountWon: 1000은 body.amountWon()으로 꺼낼 수 있습니다.
    @PostMapping // POST /api/transfers: 이체 실행이 아니라 요청 한 건 생성입니다.
    public ResponseEntity<TransferRequestService.RequestedTransfer> request(@RequestBody CreateTransfer body, @AuthenticationPrincipal Jwt jwt) {
        // amountWon을 Long으로 받아야 "금액 없음(null)"과 "0원"을 구별할 수 있습니다.
        // 여기서는 금액 누락을 잡고, 0원·음수 같은 업무 규칙은 서비스 안의 요청 객체가 검사합니다.
        if (body.amountWon() == null) {
            throw new IllegalArgumentException("Amount must be provided");
        }
        // @AuthenticationPrincipal은 보안 검사를 통과한 로그인 정보를 받는 표시입니다.
        // 금액과 계좌는 본문에서, 요청자 ID는 검증한 토큰과 DB 연결에서 가져옵니다.
        // 서비스가 오류 없이 끝나면 result에 새 요청의 ID·금액·상태 등이 담깁니다.
        TransferRequestService.RequestedTransfer result = transferRequestService.request(
                body.sourceAccountId(),
                body.destinationAccountId(),
                body.amountWon(),
                currentAppUser.resolve(jwt).userId()
        );
        // ResponseEntity는 응답의 상태 번호와 본문을 함께 정할 때 씁니다.
        // CREATED는 201입니다. "새 요청을 만들었다"는 뜻이고, 이체를 완료했다는 뜻은 아닙니다.
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // 예: POST /api/transfers/요청ID/approve. 본문 없이 Bearer 토큰만 전달합니다.
    // @PathVariable("id")는 주소의 {id}를 꺼내 transferId에 넣으라는 뜻입니다. Spring이 UUID로 읽습니다.
    // 승인 결과는 그대로 반환하며, 정상 응답은 200과 결과 JSON입니다.
    @PostMapping("/{id}/approve")
    public TransferWorkflowService.TransferResult approve(@PathVariable("id") UUID transferId,
            @AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) Map<String, Object> body) {
        rejectActionBody(body);
        return transferWorkflowService.approve(transferId, currentAppUser.resolve(jwt).userId());
    }

    // 반려도 주소에서 요청 ID, 토큰에서 결재자를 찾습니다. 상태·회사·자기 결재 검사는 서비스와 객체가 합니다.
    @PostMapping("/{id}/reject")
    public TransferWorkflowService.TransferResult reject(@PathVariable("id") UUID transferId,
            @AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) Map<String, Object> body) {
        rejectActionBody(body);
        return transferWorkflowService.reject(transferId, currentAppUser.resolve(jwt).userId());
    }

    // 승인된 요청의 모의 이체를 실행하는 주소입니다.
    // 실제 은행 호출이 아니라 우리 DB 안의 출금 계좌와 입금 계좌 잔액을 바꿉니다.
    @PostMapping("/{id}/execute")
    public TransferWorkflowService.TransferResult execute(@PathVariable("id") UUID transferId,
            @AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) Map<String, Object> body) {
        rejectActionBody(body);
        return transferWorkflowService.execute(transferId, currentAppUser.resolve(jwt).userId());
    }

    // 아래 메서드들은 이 Controller에서 발생한 오류를 HTTP 응답으로 바꾸는 담당자입니다.
    // @ExceptionHandler에 적힌 예외가 전달되면 Spring이 해당 메서드를 호출합니다.
    // ProblemDetail에는 상태 번호(status)와 이유(detail) 등이 담깁니다.
    // 예: 잔액 500원인데 1,000원을 보내려 하면 409와 잔액 부족 이유를 반환합니다.
    @ExceptionHandler({InsufficientBalanceException.class, TransferExecutionConflictException.class})
    public ProblemDetail executionConflict(RuntimeException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }

    // 자기가 낸 요청을 자기가 승인하거나 반려하려 하면 403(허용하지 않음)입니다.
    @ExceptionHandler(TransferSelfReviewException.class)
    public ProblemDetail selfReview(TransferSelfReviewException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.getMessage());
    }

    // 반려된 요청을 승인하는 등 현재 상태에서 할 수 없는 행동은 409(현재 상태와 충돌)입니다.
    @ExceptionHandler(TransferStateConflictException.class)
    public ProblemDetail stateConflict(TransferStateConflictException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }

    // 금액 누락·동일 계좌 등 잘못된 입력은 400입니다.
    // 깨진 JSON이나 UUID 변환 실패는 Spring MVC가 별도로 400으로 처리합니다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidInput(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    // 필요한 요청·사용자·계좌를 찾지 못했으면 404입니다.
    @ExceptionHandler(TransferReferenceNotFoundException.class)
    public ProblemDetail missingReference(TransferReferenceNotFoundException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    // 행동하는 사용자가 출금 계좌 회사 소속이 아니면 403입니다.
    // 로그인·역할 검사는 SecurityConfig가 먼저 처리하고, 여기서는 업무의 회사 규칙 위반을 처리합니다.
    @ExceptionHandler(TransferCompanyMismatchException.class)
    public ProblemDetail wrongCompany(TransferCompanyMismatchException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.getMessage());
    }

    // 새 요청에 필요한 입력만 받습니다. status나 createdAt은 여기서 받지 않습니다.
    // 따라서 사용자가 "이미 승인됨" 같은 상태를 이 입력 묶음으로 지정할 수 없습니다.
    public record CreateTransfer(UUID sourceAccountId, UUID destinationAccountId, Long amountWon) {
        // 예전 API의 requesterId나 임의 status를 보내면 무시하지 않고 400으로 알려줍니다.
        // @JsonAnySetter는 record에 없는 JSON 항목을 받았을 때 호출됩니다.
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Unknown transfer field: " + name);
        }
    }

    private void rejectActionBody(Map<String, Object> body) {
        // actorId로 다른 사람 행세를 할 수 없도록 처리자 입력 자체를 허용하지 않습니다.
        if (body != null && !body.isEmpty()) {
            throw new IllegalArgumentException("This action takes no body; the actor comes from the access token");
        }
    }
}
