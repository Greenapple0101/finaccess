// [이 파일의 역할] 계좌의 잔액 계산 규칙을 DB 없이 확인합니다.
// 각 시험은 계좌 준비 → 입금/출금 실행 → 남은 잔액 확인 순서입니다.
// 예를 들어 1,000원을 넣고 400원을 빼면 600원이 남아야 합니다.
// assertThat은 확인할 값을 받습니다. isEqualTo(600L)은 600원, isZero()는 0원인지 검사합니다.
// assertThatThrownBy는 잘못된 동작이 오류(예외)로 거절되는지 확인합니다.
// 거절만 확인하지 않고 잔액까지 다시 확인해야 실패한 출금이 돈을 바꾸지 않았는지 알 수 있습니다.
// @Test는 시험 한 개, @ParameterizedTest는 같은 시험을 여러 입력으로 반복하라는 표시입니다.

package io.github.greenapple0101.finaccess.account;

import io.github.greenapple0101.finaccess.company.Company;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {
    // 회사를 만들고 새 계좌를 열면, 그 회사 소유이고 잔액이 0원이어야 합니다.
    @Test
    void newAccountStartsAtZeroWon() {
        Company company = new Company("모의 회사");
        Account account = new Account(company);
        assertThat(account.getCompany()).isSameAs(company);
        assertThat(account.getBalanceWon()).isZero();
    }

    // 소유 회사 없이 계좌를 만들려고 하면 거절해야 합니다. null은 값이 없다는 뜻입니다.
    @Test
    void companyIsRequired() {
        assertThatThrownBy(() -> new Account(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Company must be provided");
    }

    // 1,000원 입금 → 400원 출금 → 남은 600원 전부 출금 순서로 계산해 봅니다.
    @Test
    void depositsAndWithdrawsIncludingEntireBalance() {
        Account account = new Account(new Company("테스트 회사"));
        account.deposit(1000L);
        account.withdraw(400L);
        assertThat(account.getBalanceWon()).isEqualTo(600L);
        account.withdraw(600L);
        assertThat(account.getBalanceWon()).isZero();
    }

    // 0원·-1원·long의 가장 작은 수를 입금과 출금에 각각 넣어 봅니다.
    // 잘못된 금액이 거절된 뒤에도 준비한 100원이 그대로 있어야 합니다.
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

    // 0원에서 1원 출금, 100원에서 101원 출금은 둘 다 거절되고 잔액은 유지되어야 합니다.
    @Test
    void rejectsInsufficientBalanceWithoutChangingIt() {
        Account account = new Account(new Company("테스트 회사"));
        assertThatThrownBy(() -> account.withdraw(1L)).isInstanceOf(InsufficientBalanceException.class);
        assertThat(account.getBalanceWon()).isZero();
        account.deposit(100L);
        assertThatThrownBy(() -> account.withdraw(101L)).isInstanceOf(InsufficientBalanceException.class);
        assertThat(account.getBalanceWon()).isEqualTo(100L);
    }

    // Long.MAX_VALUE는 long에 담을 수 있는 가장 큰 정수입니다. 실제 거래 한도는 아닙니다.
    // 그 값에서 1원을 더 넣으면 계산 오류가 나야 하고, 원래 잔액은 그대로여야 합니다.
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
