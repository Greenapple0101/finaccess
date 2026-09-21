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

@SpringBootTest
@Testcontainers
@Transactional
class AppUserPersistenceTest {
    private static final String ISSUER = "https://identity.example/realms/finaccess";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.11-bookworm");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired AppUserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @Test
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

    @Test
    void sameIdentityCannotBeRegisteredInAnotherCompany() {
        Company first = companies.saveAndFlush(new Company("회사 A"));
        Company second = companies.saveAndFlush(new Company("회사 B"));
        users.saveAndFlush(new AppUser(first, ISSUER, "user-1"));

        assertThatThrownBy(() -> users.saveAndFlush(new AppUser(second, ISSUER, "user-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

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

    @Test
    void databaseRejectsNonexistentCompany() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_users (id, company_id, identity_issuer, identity_subject) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), ISSUER, "user-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
