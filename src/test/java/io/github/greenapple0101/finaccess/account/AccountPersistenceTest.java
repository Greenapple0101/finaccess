// [이 파일의 역할] 계좌와 잔액을 실제 PostgreSQL에 저장하고 다시 읽어 봅니다.
// 회사·계좌 준비 → 저장 또는 변경 → DB 재조회 → 값 확인 순서로 읽으세요.
// Spring과 DB를 함께 검사하는 '통합 테스트'이며, Testcontainers를 위해 Docker가 필요합니다.
// @Transactional은 이 파일에서 각 시험을 한 묶음으로 실행하고 끝나면 DB 변경을 취소하게 합니다.
// 이 자동 취소를 '롤백'이라고 합니다. 실제 서비스가 저장을 확정하는 커밋 시험과는 다릅니다.
// assertThat은 값 확인, assertThatThrownBy는 잘못된 입력이 오류로 거절되는지 확인하는 도구입니다.

package io.github.greenapple0101.finaccess.account;

import io.github.greenapple0101.finaccess.company.Company;
import io.github.greenapple0101.finaccess.company.CompanyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @SpringBootTest는 실제 서비스와 저장소 등 앱의 Spring 구성을 준비합니다.
@SpringBootTest
@Testcontainers
@Transactional
class AccountPersistenceTest {

    // Testcontainers가 별도 PostgreSQL을 시작하고 종료 후 정리합니다. 이 클래스의 시험들이 사용합니다.
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.11-bookworm");

    // 새 DB의 접속 주소·계정·비밀번호를 Spring에 알려줍니다.
    // postgres::getJdbcUrl은 "주소가 필요하면 이 메서드를 호출하세요"라는 뜻입니다.
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // @Autowired는 Spring이 준비한 도구를 이 변수에 넣어 달라는 표시입니다.
    // Repository는 저장·조회, JdbcTemplate은 SQL 실행을 도와줍니다.
    @Autowired AccountRepository accounts;
    @Autowired CompanyRepository companies;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    // 같은 회사에 계좌 두 개를 저장하고 다시 조회합니다. ID는 서로 다르고, 각 잔액은 0원이어야 합니다.
    @Test
    void savesAndReloadsMultipleZeroBalanceAccounts() {
        Company company = companies.saveAndFlush(new Company("계좌 테스트 회사"));
        Account first = accounts.saveAndFlush(new Account(company));
        Account second = accounts.saveAndFlush(new Account(company));
        // clear는 JPA가 기억하는 객체를 분리합니다. DB의 행을 삭제하는 명령이 아닙니다.
        // 이후 조회가 방금 만든 메모리 객체를 그대로 돌려주는 데 그치지 않게 합니다.
        entityManager.clear();

        Account loaded = accounts.findById(first.getId()).orElseThrow();
        assertThat(loaded.getId()).isNotEqualTo(second.getId());
        assertThat(loaded.getCompany().getId()).isEqualTo(company.getId());
        assertThat(loaded.getCompany().getName()).isEqualTo("계좌 테스트 회사");
        assertThat(loaded.getBalanceWon()).isZero();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(accounts.findById(second.getId()).orElseThrow().getBalanceWon()).isZero();
    }

    // Java 코드를 건너뛰고 SQL로 -1원 계좌를 넣어도 DB가 거절해야 합니다.
    @Test
    void databaseRejectsNegativeBalance() {
        Company company = companies.saveAndFlush(new Company("테스트 회사"));
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO accounts (id, company_id, balance_won) VALUES (?, ?, ?)",
                UUID.randomUUID(), company.getId(), -1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // DB에 없는 회사 ID로 계좌를 저장하면 외래키 규칙이 거절해야 합니다.
    @Test
    void databaseRejectsMissingCompany() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO accounts (id, company_id) VALUES (?, ?)",
                UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // SQL에서 잔액 칸을 생략해도 DB가 기본값인 0원을 채우는지 확인합니다.
    @Test
    void databaseDefaultsBalanceToZero() {
        Company company = companies.saveAndFlush(new Company("테스트 회사"));
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, company_id) VALUES (?, ?)", id, company.getId());
        assertThat(accounts.findById(id).orElseThrow().getBalanceWon()).isZero();
    }

    // 1,000원 입금 뒤 300원을 빼면 700원입니다. 객체만 바꾸고 save를 다시 호출하지 않습니다.
    // JPA가 관리 중인 객체의 변경을 알아채 DB에 반영하는 것을 "변경 감지"라고 합니다.
    @Test
    void dirtyCheckingPersistsBalanceChanges() {
        Company company = companies.saveAndFlush(new Company("잔액 테스트 회사"));
        Account account = accounts.saveAndFlush(new Account(company));
        UUID id = account.getId();
        account.deposit(1000L);
        account.withdraw(300L);
        // flush는 바뀐 잔액의 SQL을 DB로 보냅니다. 저장을 최종 확정하는 commit은 아닙니다.
        entityManager.flush();
        entityManager.clear();
        assertThat(accounts.findById(id).orElseThrow().getBalanceWon()).isEqualTo(700L);
    }
}
