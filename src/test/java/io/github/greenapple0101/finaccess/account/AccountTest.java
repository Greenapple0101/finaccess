package io.github.greenapple0101.finaccess.account;

import io.github.greenapple0101.finaccess.company.Company;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// DB 없이 생성 규칙만 검사하는 단위 테스트입니다.
class AccountTest {
    @Test
    void newAccountStartsAtZeroWon() {
        Company company = new Company("모의 회사");
        Account account = new Account(company);
        assertThat(account.getCompany()).isSameAs(company);
        assertThat(account.getBalanceWon()).isZero();
    }

    @Test
    void companyIsRequired() {
        assertThatThrownBy(() -> new Account(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Company must be provided");
    }
}
