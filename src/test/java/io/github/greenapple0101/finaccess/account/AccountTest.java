// [테스트 파일을 처음 읽는 방법]
// 이 파일은 운영 요청을 처리하는 코드가 아니라, 기존 코드가 약속을 지키는지 자동 확인하는 코드입니다.
// ./gradlew test를 실행하면 JUnit이 @Test 또는 @ParameterizedTest 메서드를 찾아 실행합니다.
// 각 테스트는 준비(객체·데이터) → 실행(메서드·요청) → 검증(기대한 값)의 순서로 읽으세요.
// 테스트 메서드가 파일에 적힌 순서대로 실행된다고 가정하면 안 됩니다.
//
// assertThat(실제값).isEqualTo(기대값): 값이 같아야 성공합니다.
// isZero/isNotNull/isEmpty 등은 같은 방식으로 기대 조건을 표현합니다.
// assertThatThrownBy(() -> 호출): 람다를 실행할 때 예외가 발생하는지 확인합니다.
// 예외를 기대한 테스트에서 그 예외가 나면 테스트는 성공할 수 있습니다.
// 테스트 실패는 기대 결과와 실제 결과가 다르다는 뜻이며, 전체 프로그램이 잘못됐다는 뜻만은 아닙니다.
//
// @ParameterizedTest는 같은 검증을 여러 입력에 반복합니다.
// @ValueSource는 반복할 문자열·숫자들을 제공하고, @NullAndEmptySource는 null·빈 값을 제공합니다.
// 예를 들어 0, -1, Long.MIN_VALUE를 지정하면 각각 별도의 테스트 실행으로 집계됩니다.
// Long.MAX_VALUE/MIN_VALUE는 long 표현 범위의 끝값이며 실제 서비스 거래 한도는 아닙니다.

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
