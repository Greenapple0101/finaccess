// [이 파일의 역할: 기존 회사에 새 계좌를 개설하는 작업]
// 회사 저장소와 계좌 저장소라는 두 부품을 조합합니다.
// 회사 조회 → 없으면 중단 → 0원 Account 생성 → 저장 → 결과 반환 순서입니다.
// 두 Repository 필드가 있다고 실행 프로세스가 둘 생기는 것은 아닙니다.
// 현재 앱 한 프로세스 안에서 필요한 객체를 서로 참조하는 구조입니다.
//
// 이 서비스는 현재 입금·출금 API를 제공하지 않습니다.
// Account.deposit/withdraw는 계좌 객체의 규칙이고, 이 서비스의 open은 계좌 개설 작업입니다.
// 새 기능이 추가되어도 한꺼번에 같은 메서드에 넣지 않고 업무 단위로 구분합니다.

// package는 이 파일의 소속 주소입니다. 계층별 호출 순서를 나타내는 문법은 아닙니다.
// 아래 import는 다른 패키지의 타입을 짧은 이름으로 쓰기 위한 선언이며, 실행이나 객체 생성이 아닙니다.
package io.github.greenapple0101.finaccess.account;

import io.github.greenapple0101.finaccess.company.Company;
import io.github.greenapple0101.finaccess.company.CompanyNotFoundException;
import io.github.greenapple0101.finaccess.company.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 컴포넌트 탐색 대상이라는 표시입니다. Spring이 객체를 만들고 의존 객체를 연결합니다.
// Controller에는 보통 원본 객체 앞에서 부가 작업을 처리하는 프록시가 주입될 수 있습니다.
// 여기서는 그 프록시가 @Transactional을 읽어 트랜잭션 처리를 감쌉니다.
@Service
public class AccountService {
    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;

    // 생성자 주입입니다. 매개변수 타입에 맞는 Repository 빈을 Spring이 찾아 전달합니다.
    // 이 저장소는 우리가 구현 클래스를 쓰지 않아도 Spring Data JPA가 제공한 프록시 객체입니다.
    // Controller → Service → Repository는 폴더 중첩이 아니라 객체 참조 관계입니다.
    public AccountService(CompanyRepository companyRepository, AccountRepository accountRepository) {
        this.companyRepository = companyRepository;
        this.accountRepository = accountRepository;
    }

    // 트랜잭션은 함께 성공시키거나 실패 시 함께 되돌릴 DB 작업의 경계입니다.
    // 일반적인 외부 호출 흐름: Controller → Service 프록시 → 트랜잭션 시작 → 메서드 실행
    // → flush/commit → Controller가 결과를 받음. 커밋 자체가 실패하면 성공 응답으로 이어지지 않습니다.
    // 기본 전파 속성 REQUIRED이므로 이미 트랜잭션이 있으면 참여하고, 없으면 새로 시작합니다.
    // 기본적으로 RuntimeException/Error는 롤백 대상이며 모든 checked exception이 자동 롤백되는 것은 아닙니다.
    // 같은 객체 내부의 this.메서드() 호출은 기본 프록시 방식의 경계를 거치지 않습니다.
    // 직접 new로 만든 Service에 어노테이션만 붙어 있어도 자동 트랜잭션이 생기는 것은 아닙니다.
    @Transactional
    public OpenedAccount open(UUID companyId) {
        // 먼저 실제 회사가 있는지 확인합니다. 없으면 예외가 발생하고 다음 줄은 실행되지 않습니다.
        // 이 조회가 요청자의 회사 소속을 검사해주는 것은 아닙니다. 인증·권한은 후속 기능입니다.
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException(companyId));
        // new Account(company)는 이미 찾은 회사 참조를 받아 잔액 0원 객체를 만듭니다.
        // save가 저장을 요청하고 JPA가 UUID를 생성합니다. 저장할 회사 자체를 새로 만드는 것은 아닙니다.
        Account account = accountRepository.save(new Account(company));
        // 엔티티에서 결과에 필요한 값만 읽어 DTO를 생성합니다.
        // return은 이 메서드를 종료하지만 프록시의 커밋 처리가 끝나야 호출자에게 정상 반환됩니다.
        return new OpenedAccount(account.getId(), company.getId(), account.getBalanceWon());
    }

    // OpenedAccount는 계좌 개설 응답 데이터 타입입니다.
    // long balanceWon은 원 단위 정수이고 0부터 시작합니다. long 기본형은 null을 담을 수 없습니다.
    // 중첩 record는 AccountService.OpenedAccount라는 이름으로 다른 클래스에서 사용할 수 있습니다.
    public record OpenedAccount(UUID id, UUID companyId, long balanceWon) {
    }
}
