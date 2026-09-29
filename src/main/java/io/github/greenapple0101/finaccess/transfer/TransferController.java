package io.github.greenapple0101.finaccess.transfer;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// [HTTP 입구] JSON → 요청 record → Service → 결과 record → 응답 JSON 순서입니다.
// 아직 인증이 없는 로컬 학습용 API입니다. requesterId를 본문으로 받는 것은 로그인 검증이 아닙니다.
// 인증 연동 단계에서는 검증한 로그인 신원으로 요청자를 결정해야 합니다.
@RestController // 반환 객체를 응답 본문(JSON)으로 변환하는 Controller입니다.
@RequestMapping("/api/transfers") // 이 클래스에서 처리할 공통 주소입니다.
public class TransferController {
    private final TransferRequestService requests;
    private final TransferWorkflowService workflow;

    // 생성자 주입: Spring이 준비한 서비스 프록시를 받습니다.
    public TransferController(TransferRequestService requests, TransferWorkflowService workflow) {
        this.requests = requests;
        this.workflow = workflow;
    }

    @PostMapping // POST /api/transfers: 이체 실행이 아니라 요청 한 건 생성입니다.
    public ResponseEntity<TransferRequestService.RequestedTransfer> request(@RequestBody CreateTransfer body) {
        // Long은 null을 표현할 수 있어 누락과 0원을 구분합니다. 검증 후 long으로 자동 변환됩니다.
        if (body.amountWon() == null) {
            throw new IllegalArgumentException("Amount must be provided");
        }
        var result = requests.request(body.sourceAccountId(), body.destinationAccountId(),
                body.amountWon(), body.requesterId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // {id}의 문자열을 UUID로 변환해 전달합니다. actorId는 현재 로컬 실습용 입력입니다.
    @PostMapping("/{id}/approve")
    public TransferWorkflowService.TransferResult approve(@PathVariable UUID id, @RequestBody Actor body) {
        return workflow.approve(id, body.actorId());
    }

    @PostMapping("/{id}/reject")
    public TransferWorkflowService.TransferResult reject(@PathVariable UUID id, @RequestBody Actor body) {
        return workflow.reject(id, body.actorId());
    }

    public record Actor(UUID actorId) {
    }

    @ExceptionHandler(TransferSelfReviewException.class)
    public ProblemDetail selfReview(TransferSelfReviewException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.getMessage());
    }

    @ExceptionHandler(TransferStateConflictException.class)
    public ProblemDetail stateConflict(TransferStateConflictException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }

    // record는 요청 데이터의 모양입니다. 엔티티를 직접 받지 않아 상태·생성 시각을 임의 입력하지 않습니다.
    public record CreateTransfer(UUID sourceAccountId, UUID destinationAccountId, Long amountWon, UUID requesterId) {
    }

    // @ExceptionHandler는 이 Controller 처리 중 전달된 예외를 HTTP 응답으로 바꿉니다.
    // ProblemDetail은 status/detail 등을 담는 표준 오류 응답 객체입니다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidInput(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(TransferReferenceNotFoundException.class)
    public ProblemDetail missingReference(TransferReferenceNotFoundException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(TransferCompanyMismatchException.class)
    public ProblemDetail wrongCompany(TransferCompanyMismatchException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.getMessage());
    }
}
