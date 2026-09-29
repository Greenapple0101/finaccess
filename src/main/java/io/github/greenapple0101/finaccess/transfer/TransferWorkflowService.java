package io.github.greenapple0101.finaccess.transfer;

import java.time.Instant;
import java.util.UUID;
import io.github.greenapple0101.finaccess.account.AccountRepository;
import io.github.greenapple0101.finaccess.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// [역할] 이미 저장된 이체 요청에 결재 행동을 수행합니다. 요청 생성 서비스와 구분해 읽으세요.
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
        return new TransferResult(transfer.getId(), transfer.getStatus(), transfer.getReviewerId(), transfer.getReviewedAt());
    }

    // 결과 record는 DB 엔티티를 직접 HTTP에 노출하지 않도록 필요한 값만 담습니다.
    public record TransferResult(UUID id, TransferStatus status, UUID reviewerId, Instant reviewedAt) {
    }
}
