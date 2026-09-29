// [이 파일은 무엇을 하나요?]
// 이미 저장된 이체 요청을 승인·반려하거나, 승인된 요청의 모의 이체를 실행합니다.
// Controller가 이 서비스를 부르고, 서비스는 저장소로 찾은 객체에게 상태·잔액 변경을 맡깁니다.
// 결재 순서: 요청 잠금 → 처리자의 회사 검사 → 자기 결재·상태 검사 → 결과 저장.
// 실행 순서: 요청 잠금 → 사용자 조회 → 계좌 잠금 → 회사 검사 → 승인 확인 → 출금·입금·완료.
// HTTP 요청은 SecurityConfig의 토큰·역할 검사를 거치고, Controller가 로그인 신원으로 사용자 ID를 정합니다.
// 이 서비스는 전달받은 업무 사용자 ID로 회사·상태·잔액 규칙을 확인합니다.
package io.github.greenapple0101.finaccess.transfer;

import java.time.Instant;
import java.util.UUID;
import io.github.greenapple0101.finaccess.account.AccountRepository;
import io.github.greenapple0101.finaccess.account.Account;
import io.github.greenapple0101.finaccess.user.AppUser;
import io.github.greenapple0101.finaccess.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Service를 붙이면 Spring이 이 객체를 준비해 Controller에 전달할 수 있습니다.
@Service
public class TransferWorkflowService {
    // 처리하려면 요청서, 계좌, 사용자가 필요합니다. 각 Repository는 해당 데이터를 찾는 통로입니다.
    private final TransferRequestRepository transferRepository;
    private final AccountRepository accountRepository;
    private final AppUserRepository userRepository;

    // Spring이 세 저장소를 준비해 생성자로 전달합니다. 여기서는 받아서 필드에 보관합니다.
    public TransferWorkflowService(TransferRequestRepository transferRepository,
                                   AccountRepository accountRepository,
                                   AppUserRepository userRepository) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    // @Transactional은 이 작업의 DB 변경을 한 묶음으로 처리하도록 합니다.
    // Spring의 바깥 담당자(프록시)를 거쳐 호출될 때 적용됩니다. 기존 트랜잭션이 있으면 그 묶음에 참여합니다.
    // 새로 시작했다면 정상 처리 후 확정하고, 이 코드의 RuntimeException이 밖으로 나가면 취소합니다.
    // findTransferForReview는 잠금·회사 확인, transfer.approve는 자기 결재·상태 확인과 승인 기록을 맡습니다.
    @Transactional
    public TransferResult approve(UUID transferId, UUID reviewerId) {
        TransferRequest transfer = findTransferForReview(transferId, reviewerId);
        // 이 객체는 JPA가 지켜보고 있어 바뀐 상태를 알아차려 DB에 반영합니다.
        // 이를 '변경 감지'라고 부릅니다. 여기서 다시 save를 부를 필요가 없는 이유입니다.
        // toResult가 결과 묶음을 만들고 return이 돌려줍니다. 새 트랜잭션의 최종 확정은 바깥 Spring 처리가 맡습니다.
        transfer.approve(reviewerId);
        return toResult(transfer);
    }

    // 반려도 승인과 같은 순서로 검사합니다. 마지막에 객체의 reject를 불러 반려 상태를 기록합니다.
    @Transactional
    public TransferResult reject(UUID transferId, UUID reviewerId) {
        TransferRequest transfer = findTransferForReview(transferId, reviewerId);
        transfer.reject(reviewerId);
        return toResult(transfer);
    }

    // 출금·입금·완료 기록을 한 트랜잭션으로 묶습니다.
    // 예: A에서 1,000원을 뺐는데 B에 넣다가 실패하면 A의 출금도 취소되어야 합니다.
    // 이 메서드 밖으로 RuntimeException이 전달되면 기본 설정상 이 묶음의 DB 변경을 전부 취소합니다.
    @Transactional
    public TransferResult execute(UUID transferId, UUID actorId) {
        if (transferId == null || actorId == null) {
            throw new IllegalArgumentException("Transfer ID and actor ID must be provided");
        }

        // 먼저 요청 한 행을 잠급니다. 같은 요청을 동시에 실행해 두 번 출금하는 일을 막기 위해서입니다.
        // 이 잠금은 트랜잭션이 끝날 때 풀립니다. 다른 요청이나 DB 전체를 잠그는 것은 아닙니다.
        TransferRequest transfer = transferRepository.findLockedById(transferId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Transfer", transferId));

        AppUser actor = userRepository.findById(actorId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Actor", actorId));
        UUID sourceAccountId = transfer.getSourceAccountId();
        UUID destinationAccountId = transfer.getDestinationAccountId();

        // 두 계좌는 UUID 비교 순서대로 잠급니다. 출금 방향이 반대여도 잠그는 순서는 같아야 합니다.
        // 예: A의 ID가 B보다 앞서면 A→B 이체도, B→A 이체도 A부터 잠급니다.
        // 서로 상대 계좌의 잠금이 풀리기만 기다리는 상황(교착 상태)의 위험을 줄입니다.
        // compareTo 결과가 0보다 작으면 출금 계좌 ID가 더 앞선다는 뜻입니다.
        boolean sourceAccountComesFirst = sourceAccountId.compareTo(destinationAccountId) < 0;
        Account sourceAccount;
        Account destinationAccount;

        // 잠그는 순서는 if로 나누되, 변수에는 처음부터 출금/입금 역할에 맞는 계좌를 담습니다.
        // 잠금 전에 계좌를 일반 조회하지 않아 JPA가 옛 잔액을 기억한 채 처리하지 않게 합니다.
        if (sourceAccountComesFirst) {
            sourceAccount = findAccountWithLock(sourceAccountId);
            destinationAccount = findAccountWithLock(destinationAccountId);
        } else {
            destinationAccount = findAccountWithLock(destinationAccountId);
            sourceAccount = findAccountWithLock(sourceAccountId);
        }

        // 실행하는 사람도 출금 회사 소속이어야 합니다. 이 검사는 DB를 다시 조회하지 않습니다.
        // 이미 찾은 사용자와 잠근 계좌의 회사 ID만 비교합니다.
        checkSourceCompany(actor, sourceAccount);
        // 자기 회사의 요청임을 먼저 확인한 다음 상태를 알려줍니다.
        // 순서를 바꾸면 다른 회사 사람이 403/409 차이로 승인 여부를 추측할 수 있습니다.
        // 승인 전이거나 이미 완료된 요청은 아직 잔액을 바꾸기 전에 중단합니다.
        if (transfer.getStatus() != TransferStatus.APPROVED) {
            throw new TransferStateConflictException("Only approved transfers can be executed");
        }
        long amountWon = transfer.getAmountWon();

        // 출금은 Account가 잔액 부족을 검사합니다. 부족하면 예외가 발생해 아래 입금은 실행하지 않습니다.
        sourceAccount.withdraw(amountWon);
        // flush는 지금까지의 변경 SQL을 DB에 보내는 명령입니다. 여기서는 출금을 먼저 보냅니다.
        // 하지만 commit(최종 확정)은 아닙니다. 뒤에서 실패하면 이 출금 SQL의 결과도 취소할 수 있습니다.
        // 이 차이를 실제 테스트에서 확인하기 위해 중간 flush를 명시했습니다.
        accountRepository.flush();
        // 입금 결과가 long으로 표현할 수 있는 최대 금액을 넘으면 deposit이 오류를 냅니다.
        // catch는 그 오류를 받아 "입금 계좌 잔액 범위 초과"라는 업무 오류로 바꿔 다시 전달합니다.
        // 오류를 삼키지 않으므로 전체 작업이 취소되고, Controller에서는 409로 응답합니다.
        try {
            destinationAccount.deposit(amountWon);
        } catch (ArithmeticException overflow) {
            throw new TransferExecutionConflictException("Destination balance exceeds supported range", overflow);
        }
        // 출금·입금이 모두 통과했을 때만 완료 상태와 실행자를 기록합니다.
        // 잔액 두 개와 완료 기록은 같은 트랜잭션에서 함께 확정됩니다.
        transfer.complete(actorId);
        return toResult(transfer);
    }

    // 계좌 한 행의 쓰기 잠금을 얻어 조회합니다. 두 계좌를 잠그는 코드를 반복하지 않도록 묶었습니다.
    // 다른 트랜잭션에서 같은 행을 고치거나 충돌하는 잠금을 얻으려 하면 기다려야 합니다.
    // 보통의 읽기까지 전부 막는다는 뜻은 아닙니다. 없는 계좌라면 즉시 오류를 냅니다.
    private Account findAccountWithLock(UUID accountId) {
        return accountRepository.findLockedById(accountId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Account", accountId));
    }

    // 승인과 반려에 공통으로 필요한 준비입니다. ID 확인 → 요청 잠금 → 회사 확인을 합니다.
    // 이 메서드 자체는 private이며, 이를 부른 approve/reject의 트랜잭션 안에서 실행됩니다.
    private TransferRequest findTransferForReview(UUID transferId, UUID actorId) {
        if (transferId == null || actorId == null) {
            throw new IllegalArgumentException("Transfer ID and actor ID must be provided");
        }
        TransferRequest transfer = transferRepository.findLockedById(transferId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Transfer", transferId));
        // 결재는 잔액을 바꾸지 않으므로 출금 계좌를 일반 조회합니다. 요청 행 잠금은 이미 얻었습니다.
        AppUser reviewer = userRepository.findById(actorId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Actor", actorId));
        UUID sourceAccountId = transfer.getSourceAccountId();
        Account sourceAccount = accountRepository.findById(sourceAccountId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Source account", sourceAccountId));
        checkSourceCompany(reviewer, sourceAccount);
        return transfer;
    }

    // 결재와 실행에서 함께 쓰는 회사 검사입니다. 이미 찾은 두 객체를 받아 비교만 합니다.
    // 사용자·계좌를 몰래 다시 조회하지 않아 execute에서 얻은 계좌 잠금과 잔액을 그대로 사용합니다.
    // 실제 로그인한 본인인지, '결재자 역할'이 있는지까지 검사하는 함수는 아닙니다.
    private void checkSourceCompany(AppUser actor, Account sourceAccount) {
        UUID actorCompanyId = actor.getCompany().getId();
        UUID sourceCompanyId = sourceAccount.getCompany().getId();

        if (!actorCompanyId.equals(sourceCompanyId)) {
            throw new TransferCompanyMismatchException();
        }
    }

    // 저장용 객체에서 화면에 돌려줄 ID·상태·처리자·시각만 꺼내 결과 묶음으로 만듭니다.
    private TransferResult toResult(TransferRequest transfer) {
        return new TransferResult(
                transfer.getId(),
                transfer.getStatus(),
                transfer.getReviewerId(),
                transfer.getReviewedAt(),
                transfer.getExecutedBy(),
                transfer.getCompletedAt()
        );
    }

    // record는 결과를 전달할 값들의 묶음입니다.
    // 승인 시에는 아직 실행하지 않았으므로 executedBy와 completedAt은 비어 있을 수 있습니다.
    public record TransferResult(UUID id, TransferStatus status, UUID reviewerId, Instant reviewedAt,
                                 UUID executedBy, Instant completedAt) {
    }
}
