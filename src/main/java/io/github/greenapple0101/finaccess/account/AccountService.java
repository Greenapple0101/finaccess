package io.github.greenapple0101.finaccess.account;

import io.github.greenapple0101.finaccess.company.Company;
import io.github.greenapple0101.finaccess.company.CompanyNotFoundException;
import io.github.greenapple0101.finaccess.company.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// [만든 순서 1] 먼저 기존 저장 기능을 조합해 '계좌 개설' 작업을 만듭니다.
// HTTP를 다루지 않으며 회사 확인과 계좌 저장을 하나의 트랜잭션으로 묶습니다.
@Service
public class AccountService {
    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;

    // 회사 조회와 계좌 저장에 필요한 두 Repository를 Spring이 연결해 줍니다.
    public AccountService(CompanyRepository companyRepository, AccountRepository accountRepository) {
        this.companyRepository = companyRepository;
        this.accountRepository = accountRepository;
    }

    // 정상 종료 시 커밋한 다음 Controller로 결과를 돌려줍니다.
    // 회사가 없으면 예외를 던져 계좌를 만들지 않습니다.
    // 회사 존재 확인은 접근 권한 검사가 아닙니다. 소속·권한 검사는 이후 인증 단계에서 추가합니다.
    @Transactional
    public OpenedAccount open(UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException(companyId));
        Account account = accountRepository.save(new Account(company));
        return new OpenedAccount(account.getId(), company.getId(), account.getBalanceWon());
    }

    // 생성 결과만 담는 DTO입니다. balanceWon은 원 단위 잔액이며 항상 0부터 시작합니다.
    // DB 생성 시각은 저장 직후 자동 반영되지 않으므로 이번 응답에는 포함하지 않습니다.
    public record OpenedAccount(UUID id, UUID companyId, long balanceWon) {
    }
}
