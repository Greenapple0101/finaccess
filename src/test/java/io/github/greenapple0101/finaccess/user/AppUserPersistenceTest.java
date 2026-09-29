// [이 파일의 역할] 사용자를 저장하고 다시 읽어도 회사 소속과 로그인 식별 정보가 유지되는지 확인합니다.
// 회사·사용자 준비 → DB 저장 → 메모리의 객체 분리 → DB 재조회 → 값 확인 순서로 읽으세요.
// issuer는 로그인 정보를 발급한 곳, subject는 그곳에서 사용하는 사용자 번호입니다.
// 예를 들어 학교가 다르면 학번 1번이 각각 있어도 다른 학생인 것과 비슷합니다.
// Spring과 실제 PostgreSQL을 함께 사용하는 '통합 테스트'라서 Docker가 필요합니다.
// @Transactional 때문에 각 시험이 끝나면 그 시험의 DB 변경은 자동으로 취소됩니다(롤백).
// assertThat은 값 확인, assertThatThrownBy는 잘못된 저장이 오류로 거절되는지 확인하는 도구입니다.

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

// @SpringBootTest는 실제 서비스와 저장소 등 앱의 Spring 구성을 준비합니다.
@SpringBootTest
@Testcontainers
@Transactional
class AppUserPersistenceTest {
    private static final String ISSUER = "https://identity.example/realms/finaccess";

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
    @Autowired AppUserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    // 회사 한 곳에 사용자 두 명을 저장합니다. 다시 읽었을 때 소속과 두 사용자가 모두 남아야 합니다.
    @Test
    void multipleUsersCanBelongToOneCompanyAndBeReloadedByIdentity() {
        Company company = companies.saveAndFlush(new Company("테스트 회사"));
        AppUser first = users.saveAndFlush(new AppUser(company, ISSUER, "user-1"));
        users.saveAndFlush(new AppUser(company, ISSUER, "user-2"));
        // clear는 JPA가 기억하는 객체를 분리합니다. DB의 행을 삭제하는 명령이 아닙니다.
        // 이후 조회가 방금 만든 메모리 객체를 그대로 돌려주는 데 그치지 않게 합니다.
        entityManager.clear();

        AppUser loaded = users.findByIdentityIssuerAndIdentitySubject(ISSUER, "user-1").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(first.getId());
        assertThat(loaded.getCompany().getId()).isEqualTo(company.getId());
        assertThat(loaded.getCompany().getName()).isEqualTo("테스트 회사");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(users.findByIdentityIssuerAndIdentitySubject(ISSUER, "user-2")).isPresent();
    }

    // 같은 발급자와 사용자 번호를 회사만 바꿔 두 번 등록하면 DB가 거절해야 합니다.
    // saveAndFlush는 저장 SQL을 지금 보내도록 해서 이 줄에서 DB의 거절을 확인하게 합니다.
    @Test
    void sameIdentityCannotBeRegisteredInAnotherCompany() {
        Company first = companies.saveAndFlush(new Company("회사 A"));
        Company second = companies.saveAndFlush(new Company("회사 B"));
        users.saveAndFlush(new AppUser(first, ISSUER, "user-1"));

        assertThatThrownBy(() -> users.saveAndFlush(new AppUser(second, ISSUER, "user-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // 사용자 번호가 user-1로 같아도 발급자가 다르면 다른 로그인 신원으로 구분해야 합니다.
    @Test
    void sameSubjectFromDifferentIssuersRemainsDistinct() {
        Company company = companies.saveAndFlush(new Company("테스트 회사"));
        AppUser first = users.saveAndFlush(new AppUser(company, ISSUER, "user-1"));
        AppUser second = users.saveAndFlush(new AppUser(company, "https://other.example/realms/finaccess", "user-1"));
        entityManager.clear();

        assertThat(users.findByIdentityIssuerAndIdentitySubject(ISSUER, "user-1").orElseThrow().getId())
                .isEqualTo(first.getId()).isNotEqualTo(second.getId());
        assertThat(users.findByIdentityIssuerAndIdentitySubject(ISSUER, "missing")).isEmpty();
    }

    // Java 생성자의 검사를 거치지 않고 SQL로 없는 회사 ID를 넣습니다.
    // 외래키는 "연결한 회사가 실제로 있어야 한다"는 DB 규칙이며, 이것이 거절하는지 확인합니다.
    @Test
    void databaseRejectsNonexistentCompany() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_users (id, company_id, identity_issuer, identity_subject) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), ISSUER, "user-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
