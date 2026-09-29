// [기존 회사에 0원 계좌를 만드는 담당자]
// 처리 순서: 회사 찾기 → 없으면 중단 → Account 만들기 → 저장 → 계좌 개설 결과 반환.
// 회사 확인에는 CompanyRepository, 계좌 저장에는 AccountRepository가 필요합니다.
// 저장소가 두 개여도 서버가 두 개 생기는 것은 아닙니다. 이 앱 안에서 필요한 기능을 나누어 쓰는 것입니다.
//
// 이 파일의 open은 계좌 개설만 담당합니다. 계좌의 입출금 규칙은 Account에,
// 계좌 간 이체 처리 순서는 TransferWorkflowService에 있습니다.

package io.github.greenapple0101.finaccess.account;

import io.github.greenapple0101.finaccess.company.Company;
import io.github.greenapple0101.finaccess.company.CompanyNotFoundException;
import io.github.greenapple0101.finaccess.company.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Spring이 이 클래스를 업무 담당 객체로 만들고, 다른 객체가 사용할 수 있게 관리합니다.
@Service
public class AccountService {
    // 필드는 객체가 사용할 값을 보관하는 자리입니다. 여기에는 두 종류의 저장소를 보관합니다.
    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;

    // Spring이 회사를 찾을 저장소와 계좌를 저장할 저장소를 전달해 줍니다.
    // 필요한 객체를 생성자로 받아 연결하는 것이 "생성자 주입"입니다.
    public AccountService(CompanyRepository companyRepository, AccountRepository accountRepository) {
        this.companyRepository = companyRepository;
        this.accountRepository = accountRepository;
    }

    // 회사 조회와 계좌 저장을 하나의 DB 작업 묶음(트랜잭션)에서 처리합니다.
    // 일반적인 Controller 호출에서는 Spring이 이 메서드 앞뒤에서 시작·확정·실패 시 되돌리기를 맡습니다.
    // 메서드가 return에 도달해도 DB 변경 확정이 실패하면 성공 응답을 보내지 않습니다.
    // 직접 new로 만든 Service를 호출하면 이런 자동 처리가 붙지 않습니다.
    @Transactional
    public OpenedAccount open(UUID companyId) {
        // 1. 회사가 실제로 있는지 찾습니다. 없으면 orElseThrow가 예외를 보내 다음 줄로 넘어가지 않습니다.
        // () -> new ... 는 회사가 없을 때만 예외를 만드는 짧은 함수 표현입니다.
        // 이 조회는 "이 사람이 이 회사의 계좌를 만들어도 되는지"까지 검사하지 않습니다.
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException(companyId));
        // 2. 방금 찾은 회사로 0원 계좌 객체를 만들고 저장을 요청합니다.
        // 회사 자체를 새로 등록하는 것이 아닙니다. 기존 회사에 새 계좌 하나를 연결합니다.
        // JPA가 계좌 UUID를 만들며 실제 INSERT SQL은 트랜잭션 마무리 시점까지 늦춰질 수 있습니다.
        Account account = accountRepository.save(new Account(company));
        // 3. 응답에 필요한 계좌 ID·회사 ID·잔액만 골라 결과로 돌려줍니다.
        // Controller가 이 결과를 받아 HTTP 201 응답의 JSON 본문에 넣습니다.
        return new OpenedAccount(account.getId(), company.getId(), account.getBalanceWon());
    }

    // record는 결과값을 한 묶음으로 전달하기 위한 Java 문법입니다.
    // AccountService.OpenedAccount라는 이름으로 다른 파일에서도 사용합니다.
    // 이름을 안쪽에 선언했다고 실행 단계가 추가되는 것은 아닙니다.
    public record OpenedAccount(UUID id, UUID companyId, long balanceWon) {
    }
}
