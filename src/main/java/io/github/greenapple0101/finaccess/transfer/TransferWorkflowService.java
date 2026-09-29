package io.github.greenapple0101.finaccess.transfer;

import java.time.Instant;
import java.util.UUID;
import io.github.greenapple0101.finaccess.account.AccountRepository;
import io.github.greenapple0101.finaccess.account.Account;
import io.github.greenapple0101.finaccess.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// [역할] 저장된 이체 요청의 결재와 모의 내부 이체를 수행합니다. 요청 생성 서비스와 구분해 읽으세요.
@Service
public class TransferWorkflowService {
    private final TransferRequestRepository transfers;
    private final AccountRepository accounts;
    private final AppUserRepository users;

    public TransferWorkflowService(TransferRequestRepository transfers, AccountRepository accounts, AppUserRepository users) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.users = users;
    }

    // 외부 호출이 Spring 프록시를 거칠 때 조회 잠금부터 상태 저장까지 트랜잭션으로 묶습니다.
    @Transactional
    public TransferResult approve(UUID id, UUID reviewerId) {
        TransferRequest transfer = reviewable(id, reviewerId);
        transfer.approve(reviewerId);
        // 관리 중인 엔티티이므로 save를 다시 호출하지 않아도 변경 감지로 저장됩니다.
        return result(transfer);
    }

    @Transactional
    public TransferResult reject(UUID id, UUID reviewerId) {
        TransferRequest transfer = reviewable(id, reviewerId);
        transfer.reject(reviewerId);
        return result(transfer);
    }

    // 요청 잠금 → 상태 확인 → 사용자 조회 → 계좌 잠금 → 회사 확인 → 출금 → 입금 → 완료 순서입니다.
    // 하나라도 RuntimeException이면 출금 SQL이 이미 실행됐더라도 전부 롤백합니다.
    @Transactional
    public TransferResult execute(UUID id, UUID actorId) {
        if (id == null || actorId == null) {
            throw new IllegalArgumentException("Transfer ID and actor ID must be provided");
        }
        TransferRequest transfer = transfers.findLockedById(id)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Transfer", id));
        // 아직 승인되지 않았거나 이미 완료됐다면 잔액에 접근하기 전에 중단합니다.
        if (transfer.getStatus() != TransferStatus.APPROVED) {
            throw new TransferStateConflictException("Only approved transfers can be executed");
        }
        var actor = users.findById(actorId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Actor", actorId));
        UUID sourceId = transfer.getSourceAccountId();
        UUID destinationId = transfer.getDestinationAccountId();
        // UUID의 일정한 순서로 잠금을 획득합니다. A→B와 B→A 모두 같은 계좌부터 잠급니다.
        // 잠금 전 잔액을 일반 조회하지 않아 영속성 컨텍스트에 오래된 잔액이 남지 않게 합니다.
        UUID firstId = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
        UUID secondId = firstId.equals(sourceId) ? destinationId : sourceId;
        Account first = lockedAccount(firstId);
        Account second = lockedAccount(secondId);
        Account source = firstId.equals(sourceId) ? first : second;
        Account destination = firstId.equals(sourceId) ? second : first;
        if (!source.getCompany().getId().equals(actor.getCompany().getId())) {
            throw new TransferCompanyMismatchException();
        }
        source.withdraw(transfer.getAmountWon());
        // 교육 목적의 명시적 flush: 출금 UPDATE를 DB에 보내지만 아직 commit하지 않습니다.
        // 이후 입금 실패 시 이 SQL도 취소됩니다. flush와 commit의 차이를 테스트로 확인합니다.
        accounts.flush();
        try {
            destination.deposit(transfer.getAmountWon());
        } catch (ArithmeticException overflow) {
            throw new TransferExecutionConflictException("Destination balance exceeds supported range", overflow);
        }
        transfer.complete(actorId);
        return result(transfer);
    }

    private Account lockedAccount(UUID id) {
        return accounts.findLockedById(id)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Account", id));
    }

    private TransferRequest reviewable(UUID id, UUID actorId) {
        if (id == null || actorId == null) {
            throw new IllegalArgumentException("Transfer ID and actor ID must be provided");
        }
        TransferRequest transfer = transfers.findLockedById(id)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Transfer", id));
        requireSourceCompany(transfer, actorId);
        return transfer;
    }

    // 회사 ID를 비교하는 업무 검사입니다. 아직 역할 또는 실제 로그인 신원 검사는 아닙니다.
    private void requireSourceCompany(TransferRequest transfer, UUID actorId) {
        var actor = users.findById(actorId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Actor", actorId));
        var source = accounts.findById(transfer.getSourceAccountId())
                .orElseThrow(() -> new TransferReferenceNotFoundException("Source account", transfer.getSourceAccountId()));
        if (!source.getCompany().getId().equals(actor.getCompany().getId())) {
            throw new TransferCompanyMismatchException();
        }
    }

    private TransferResult result(TransferRequest transfer) {
        return new TransferResult(transfer.getId(), transfer.getStatus(), transfer.getReviewerId(), transfer.getReviewedAt(),
                transfer.getExecutedBy(), transfer.getCompletedAt());
    }

    // 결과 record는 DB 엔티티를 직접 HTTP에 노출하지 않도록 필요한 값만 담습니다.
    public record TransferResult(UUID id, TransferStatus status, UUID reviewerId, Instant reviewedAt,
                                 UUID executedBy, Instant completedAt) {
    }
}
