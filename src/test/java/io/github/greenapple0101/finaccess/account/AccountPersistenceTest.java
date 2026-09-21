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

// 계좌와 회사의 관계를 실제 PostgreSQL에서 검증하는 통합 테스트입니다.
// 클래스의 Transactional은 각 테스트를 트랜잭션으로 실행하고 종료 후 기본적으로 롤백합니다.
// 제약 위반 테스트는 예외를 예상하는 테스트이며 실패 로그가 보여도 의도한 결과일 수 있습니다.
@SpringBootTest
@Testcontainers
@Transactional
class AccountPersistenceTest {

    // 테스트 전용 DB 컨테이너입니다. Compose의 개발용 DB와 분리하며 종료 후 정리됩니다.
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.11-bookworm");

    // 컨테이너가 정한 접속 주소·계정·비밀번호를 Spring 테스트 설정에 전달합니다.
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired AccountRepository accounts;
    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired CompanyRepository companies;
    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired EntityManager entityManager;
    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired JdbcTemplate jdbc;

    // [만든 순서 4] 저장 후 메모리의 객체를 비우고 DB에서 다시 읽습니다.
    // 같은 회사가 여러 계좌를 가질 수 있으며 각 계좌는 0원으로 시작해야 합니다.
    @Test
    void savesAndReloadsMultipleZeroBalanceAccounts() {
        Company company = companies.saveAndFlush(new Company("계좌 테스트 회사"));
        Account first = accounts.saveAndFlush(new Account(company));
        Account second = accounts.saveAndFlush(new Account(company));
        entityManager.clear();

        Account loaded = accounts.findById(first.getId()).orElseThrow();
        assertThat(loaded.getId()).isNotEqualTo(second.getId());
        assertThat(loaded.getCompany().getId()).isEqualTo(company.getId());
        assertThat(loaded.getCompany().getName()).isEqualTo("계좌 테스트 회사");
        assertThat(loaded.getBalanceWon()).isZero();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(accounts.findById(second.getId()).orElseThrow().getBalanceWon()).isZero();
    }

    // Java를 거치지 않은 직접 SQL에도 잔액의 DB 제약이 적용되는지 확인합니다.
    @Test
    void databaseRejectsNegativeBalance() {
        Company company = companies.saveAndFlush(new Company("테스트 회사"));
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO accounts (id, company_id, balance_won) VALUES (?, ?, ?)",
                UUID.randomUUID(), company.getId(), -1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsMissingCompany() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO accounts (id, company_id) VALUES (?, ?)",
                UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // SQL에서 잔액을 생략했을 때에도 DB 기본값 0이 적용되어야 합니다.
    @Test
    void databaseDefaultsBalanceToZero() {
        Company company = companies.saveAndFlush(new Company("테스트 회사"));
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, company_id) VALUES (?, ?)", id, company.getId());
        assertThat(accounts.findById(id).orElseThrow().getBalanceWon()).isZero();
    }
}
