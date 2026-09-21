package io.github.greenapple0101.finaccess.account;

import io.github.greenapple0101.finaccess.company.Company;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// DB 없이 생성·입출금 규칙을 검사하는 단위 테스트입니다.
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

    // 입금 뒤 일부를 출금하고, 나머지 전액 출금까지 가능한지 확인합니다.
    @Test
    void depositsAndWithdrawsIncludingEntireBalance() {
        Account account = new Account(new Company("테스트 회사"));
        account.deposit(1000L);
        account.withdraw(400L);
        assertThat(account.getBalanceWon()).isEqualTo(600L);
        account.withdraw(600L);
        assertThat(account.getBalanceWon()).isZero();
    }

    // 각 경계값을 양쪽 메서드에 적용하고 실패 후 잔액이 그대로인지 검사합니다.
    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonpositiveAmountsWithoutChangingBalance(long amount) {
        Account account = new Account(new Company("테스트 회사"));
        account.deposit(100L);
        assertThatThrownBy(() -> account.deposit(amount)).isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getBalanceWon()).isEqualTo(100L);
        assertThatThrownBy(() -> account.withdraw(amount)).isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getBalanceWon()).isEqualTo(100L);
    }

    @Test
    void rejectsInsufficientBalanceWithoutChangingIt() {
        Account account = new Account(new Company("테스트 회사"));
        assertThatThrownBy(() -> account.withdraw(1L)).isInstanceOf(InsufficientBalanceException.class);
        assertThat(account.getBalanceWon()).isZero();
        account.deposit(100L);
        assertThatThrownBy(() -> account.withdraw(101L)).isInstanceOf(InsufficientBalanceException.class);
        assertThat(account.getBalanceWon()).isEqualTo(100L);
    }

    // 자료형의 표현 범위를 검사하는 테스트이며 실제 서비스의 거래 한도는 아닙니다.
    @Test
    void rejectsOverflowAndPreservesMaximumBalance() {
        Account account = new Account(new Company("테스트 회사"));
        account.deposit(Long.MAX_VALUE);
        assertThatThrownBy(() -> account.deposit(1L)).isInstanceOf(ArithmeticException.class);
        assertThat(account.getBalanceWon()).isEqualTo(Long.MAX_VALUE);
        account.withdraw(Long.MAX_VALUE);
        assertThat(account.getBalanceWon()).isZero();
    }
}
