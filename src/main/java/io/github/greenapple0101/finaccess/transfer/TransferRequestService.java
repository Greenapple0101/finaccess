package io.github.greenapple0101.finaccess.transfer;

import io.github.greenapple0101.finaccess.account.Account;
import io.github.greenapple0101.finaccess.account.AccountRepository;
import io.github.greenapple0101.finaccess.user.AppUser;
import io.github.greenapple0101.finaccess.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

// [역할] 요청 객체의 검사만으로 알 수 없는 실제 DB 존재 여부와 회사 소속을 확인합니다.
// 객체 생성 → 요청자 조회 → 출금 계좌 조회·소속 확인 → 입금 계좌 조회 → 저장 순서입니다.
// 아직 Controller나 로그인 연동은 없습니다. requesterId가 실제 호출자의 신원인지는 검증하지 않습니다.
@Service // Spring이 이 클래스를 찾아 서비스 객체(Bean)를 만들도록 표시합니다.
public class TransferRequestService {
    // final은 생성자에서 주입받은 저장소 참조를 다른 참조로 재대입하지 못하게 합니다.
    private final AppUserRepository users;
    private final AccountRepository accounts;
    private final TransferRequestRepository transfers;

    // 생성자 주입: Spring이 준비한 세 저장소를 받습니다. 저장소마다 서버가 생기는 것은 아닙니다.
    public TransferRequestService(AppUserRepository users, AccountRepository accounts,
                                  TransferRequestRepository transfers) {
        this.users = users;
        this.accounts = accounts;
        this.transfers = transfers;
    }

    // 외부에서 Spring 프록시를 통해 호출하면 조회·저장을 한 트랜잭션으로 감쌉니다.
    // RuntimeException 발생 시 기본적으로 롤백합니다. 직접 new로 만든 서비스에는 적용되지 않습니다.
    // 트랜잭션이 있다고 로그인 검증이나 동시 요청 잠금이 자동으로 생기지는 않습니다.
    @Transactional
    public RequestedTransfer request(UUID sourceAccountId, UUID destinationAccountId,
                                     long amountWon, UUID requesterId) {
        // 생성자가 null ID, 동일 계좌, 0·음수 금액을 먼저 거부합니다. 아직 INSERT는 없습니다.
        TransferRequest transfer = new TransferRequest(sourceAccountId, destinationAccountId, amountWon, requesterId);
        // findById의 Optional은 조회 결과가 없을 수도 있다는 표현입니다.
        // orElseThrow는 결과가 없으면 람다(() -> ...)로 만든 예외를 던지고 다음 실행을 중단합니다.
        AppUser requester = users.findById(requesterId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Requester", requesterId));
        Account source = accounts.findById(sourceAccountId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Source account", sourceAccountId));
        // 회사 이름이나 객체 주소가 아닌 회사 UUID 값을 비교합니다.
        // !는 결과를 반대로 바꿉니다. 두 회사가 다를 때 예외를 던집니다.
        if (!requester.getCompany().getId().equals(source.getCompany().getId())) {
            throw new TransferCompanyMismatchException();
        }
        // 수취 계좌는 다른 회사 소유도 허용합니다. 회사 간 송금 요청을 표현하기 위한 정책입니다.
        // 입금 계좌 상세 정보는 반환하지 않고 존재 여부만 확인합니다.
        accounts.findById(destinationAccountId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Destination account", destinationAccountId));
        // 잔액을 차감하거나 예약하지 않습니다. 잔액 검사는 실제 실행 시점에 다시 해야 합니다.
        TransferRequest saved = transfers.save(transfer);
        // 엔티티 전체 대신 결과에 필요한 값만 record로 반환합니다.
        // 새 트랜잭션이면 프록시의 commit까지 성공해야 호출자가 결과를 받습니다.
        return new RequestedTransfer(saved.getId(), saved.getSourceAccountId(), saved.getDestinationAccountId(),
                saved.getAmountWon(), saved.getRequesterId(), saved.getStatus());
    }

    // record는 전달할 데이터 묶음입니다. 필드 이름과 같은 접근자(id(), status() 등)를 제공합니다.
    // createdAt은 DB에서 생성되어 즉시 객체에 채워지지 않으므로 이번 결과에는 포함하지 않습니다.
    public record RequestedTransfer(UUID id, UUID sourceAccountId, UUID destinationAccountId,
                                    long amountWon, UUID requesterId, TransferStatus status) {
    }
}
