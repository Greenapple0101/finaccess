// [이 파일은 무엇을 하나요?]
// 새 이체 요청을 저장하기 전에 실제 사용자·계좌가 있는지, 회사 소속이 맞는지 확인합니다.
// 순서: 입력 검사 → 요청자 찾기 → 출금 계좌 찾기 → 회사 비교 → 입금 계좌 찾기 → 요청 저장.
// 예: A회사 직원은 A회사 계좌에서 B회사 계좌로 보내는 요청을 만들 수 있습니다.
// 이 단계에서는 요청서만 저장합니다. 돈을 빼거나 미리 잡아 두는 일은 하지 않습니다.
package io.github.greenapple0101.finaccess.transfer;

import io.github.greenapple0101.finaccess.account.Account;
import io.github.greenapple0101.finaccess.account.AccountRepository;
import io.github.greenapple0101.finaccess.user.AppUser;
import io.github.greenapple0101.finaccess.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service // Spring이 이 클래스를 찾아 서비스 객체(Bean)를 만들도록 표시합니다.
public class TransferRequestService {
    // Repository는 DB에서 값을 찾거나 저장하는 통로입니다. 사용자·계좌·요청별로 통로가 하나씩 있습니다.
    // final은 생성자에서 받은 통로를 나중에 다른 통로로 바꾸지 못하게 합니다.
    private final AppUserRepository users;
    private final AccountRepository accounts;
    private final TransferRequestRepository transfers;

    // Spring이 준비한 세 저장소를 생성자로 받습니다. 각 저장소는 같은 애플리케이션 안에서 사용합니다.
    public TransferRequestService(AppUserRepository users, AccountRepository accounts,
                                  TransferRequestRepository transfers) {
        this.users = users;
        this.accounts = accounts;
        this.transfers = transfers;
    }

    // 트랜잭션은 DB 작업을 "모두 성공하거나, 실패하면 취소할 한 묶음"으로 다루는 단위입니다.
    // Spring이 붙여 주는 바깥 담당자(프록시)가 이 메서드 앞뒤에서 시작과 확정(commit)을 처리합니다.
    // RuntimeException 계열 오류가 밖으로 나오면 기본 설정상 취소(rollback)합니다.
    // 직접 new로 만든 서비스를 부르거나 이 객체 안에서 직접 부르면 이 처리가 자동 적용되지 않습니다.
    // 로그인 검사나 계좌 잠금은 @Transactional이 대신 해 주지 않습니다.
    @Transactional
    public RequestedTransfer request(UUID sourceAccountId, UUID destinationAccountId,
                                     long amountWon, UUID requesterId) {
        // 먼저 요청 객체를 만듭니다. 생성자가 ID 누락·동일 계좌·0원 이하 금액을 거부합니다.
        // 예: 출금·입금 계좌 ID가 같으면 여기서 멈춥니다. 아직 DB에 요청을 저장하지 않았습니다.
        TransferRequest transfer = new TransferRequest(sourceAccountId, destinationAccountId, amountWon, requesterId);
        // findById는 "찾았을 수도, 없을 수도 있다"는 결과(Optional)를 돌려줍니다.
        // orElseThrow는 찾았다면 객체를 꺼내고, 없으면 괄호 안 예외를 발생시킵니다.
        // () ->는 "필요해지면 이 예외를 만들어라"라는 짧은 함수 표현입니다.
        AppUser requester = users.findById(requesterId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Requester", requesterId));
        Account source = accounts.findById(sourceAccountId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Source account", sourceAccountId));
        // 요청자의 회사 ID와 출금 계좌의 회사 ID를 비교합니다.
        // equals는 값이 같은지 묻고, 앞의 !는 답을 뒤집습니다. 회사가 다르면 거부합니다.
        // 이름이 같은 회사가 있을 수 있으므로 회사 이름 대신 고유한 ID를 비교합니다.
        // 다만 requesterId는 아직 입력받은 값입니다. 호출자가 실제 그 사람인지는 확인하지 않습니다.
        if (!requester.getCompany().getId().equals(source.getCompany().getId())) {
            throw new TransferCompanyMismatchException();
        }
        // 입금 계좌는 존재만 확인합니다. 수취인은 다른 회사여도 송금 요청을 받을 수 있습니다.
        // 출금 계좌와 달리 요청자의 회사 소유일 필요는 없습니다.
        accounts.findById(destinationAccountId)
                .orElseThrow(() -> new TransferReferenceNotFoundException("Destination account", destinationAccountId));
        // 검사를 통과한 요청을 저장 대상으로 등록합니다. 잔액은 건드리지 않습니다.
        // 요청 때 잔액이 충분해도 나중에 바뀔 수 있어 실제 잔액 검사는 실행 서비스가 합니다.
        TransferRequest saved = transfers.save(transfer);
        // 저장용 객체 전체 대신 호출자에게 필요한 값만 새 묶음으로 만듭니다.
        // 새 트랜잭션을 시작한 호출이라면, Spring의 최종 확정까지 성공해야 Controller에 결과가 돌아갑니다.
        return new RequestedTransfer(saved.getId(), saved.getSourceAccountId(), saved.getDestinationAccountId(),
                saved.getAmountWon(), saved.getRequesterId(), saved.getStatus());
    }

    // record는 결과를 전달하는 데이터 묶음입니다. id(), status()처럼 이름으로 값을 꺼냅니다.
    // 생성 시각은 DB가 정하고 새 객체에는 바로 채워지지 않으므로 이 결과에서 제외했습니다.
    public record RequestedTransfer(UUID id, UUID sourceAccountId, UUID destinationAccountId,
                                    long amountWon, UUID requesterId, TransferStatus status) {
    }
}
