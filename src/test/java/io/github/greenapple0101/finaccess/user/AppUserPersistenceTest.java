package io.github.greenapple0101.finaccess.user;

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

// 사용자와 회사의 관계를 실제 PostgreSQL에서 검증하는 통합 테스트입니다.
// 클래스의 Transactional은 각 테스트를 트랜잭션으로 실행하고 종료 후 기본적으로 롤백합니다.
// 제약 위반 테스트는 예외를 예상하는 테스트이며 실패 로그가 보여도 의도한 결과일 수 있습니다.
@SpringBootTest
@Testcontainers
@Transactional
class AppUserPersistenceTest {
    private static final String ISSUER = "https://identity.example/realms/finaccess";

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
    @Autowired AppUserRepository users;
    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired CompanyRepository companies;
    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired EntityManager entityManager;
    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired JdbcTemplate jdbc;

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 같은 회사에 사용자 둘을 저장한 후 영속성 컨텍스트를 비우고 다시 읽습니다.
    // 사용자의 외래키, 연결된 회사명, DB 생성 시각을 함께 확인합니다.
    void multipleUsersCanBelongToOneCompanyAndBeReloadedByIdentity() {
        Company company = companies.saveAndFlush(new Company("테스트 회사"));
        AppUser first = users.saveAndFlush(new AppUser(company, ISSUER, "user-1"));
        users.saveAndFlush(new AppUser(company, ISSUER, "user-2"));
        entityManager.clear();

        AppUser loaded = users.findByIdentityIssuerAndIdentitySubject(ISSUER, "user-1").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(first.getId());
        assertThat(loaded.getCompany().getId()).isEqualTo(company.getId());
        assertThat(loaded.getCompany().getName()).isEqualTo("테스트 회사");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(users.findByIdentityIssuerAndIdentitySubject(ISSUER, "user-2")).isPresent();
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 같은 issuer·subject 조합으로 회사만 바꾸어 중복 등록할 수 없음을 검증합니다.
    // saveAndFlush로 제약 검사가 메서드 호출 중 발생하도록 합니다.
    void sameIdentityCannotBeRegisteredInAnotherCompany() {
        Company first = companies.saveAndFlush(new Company("회사 A"));
        Company second = companies.saveAndFlush(new Company("회사 B"));
        users.saveAndFlush(new AppUser(first, ISSUER, "user-1"));

        assertThatThrownBy(() -> users.saveAndFlush(new AppUser(second, ISSUER, "user-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // subject가 같아도 발급자가 다르면 다른 신원입니다. 두 사용자를 구분하는지 확인합니다.
    void sameSubjectFromDifferentIssuersRemainsDistinct() {
        Company company = companies.saveAndFlush(new Company("테스트 회사"));
        AppUser first = users.saveAndFlush(new AppUser(company, ISSUER, "user-1"));
        AppUser second = users.saveAndFlush(new AppUser(company, "https://other.example/realms/finaccess", "user-1"));
        entityManager.clear();

        assertThat(users.findByIdentityIssuerAndIdentitySubject(ISSUER, "user-1").orElseThrow().getId())
                .isEqualTo(first.getId()).isNotEqualTo(second.getId());
        assertThat(users.findByIdentityIssuerAndIdentitySubject(ISSUER, "missing")).isEmpty();
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 직접 SQL로 없는 company_id를 넣어 외래키가 거부하는지 확인합니다.
    // Java 검사만 우회하면 잘못된 데이터가 저장되는 구조가 아닌지 검증합니다.
    void databaseRejectsNonexistentCompany() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_users (id, company_id, identity_issuer, identity_subject) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), ISSUER, "user-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
