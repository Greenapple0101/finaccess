package io.github.greenapple0101.finaccess;

import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import io.github.greenapple0101.finaccess.company.Company;
import io.github.greenapple0101.finaccess.company.CompanyRepository;
import org.flywaydb.core.Flyway;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class FinaccessApplicationTests {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.11-bookworm");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Flyway flyway;

    @Test
    void connectsToPostgresql() {
        assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
                .isEqualTo(postgres.getDatabaseName());
    }

    @Test
    @Transactional
    void migratedCompanyTableStoresData() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, ?)", id, "FinAccess Demo");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM companies WHERE id = ?", String.class, id))
                .isEqualTo("FinAccess Demo");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_at IS NOT NULL FROM companies WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    void migrationIsRecordedAndNotAppliedTwice() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class))
                .isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    void savesAndReloadsCompanyFromDatabase() {
        Company saved = companyRepository.saveAndFlush(new Company("테스트 회사"));
        UUID id = saved.getId();
        assertThat(id).isNotNull();

        // Discard managed objects so findById must read the database.
        entityManager.clear();
        Company reloaded = companyRepository.findById(id).orElseThrow();

        assertThat(reloaded).isNotSameAs(saved);
        assertThat(reloaded.getName()).isEqualTo("테스트 회사");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    @Transactional
    void missingCompanyReturnsEmpty() {
        assertThat(companyRepository.findById(UUID.randomUUID())).isEmpty();
    }
}
