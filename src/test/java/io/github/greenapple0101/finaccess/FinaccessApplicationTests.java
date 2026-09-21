package io.github.greenapple0101.finaccess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
@AutoConfigureMockMvc
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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void registrationCommitsCompanyAndReturnsCreated() throws Exception {
        String response = mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"등록 테스트 회사\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("등록 테스트 회사"))
                .andExpect(jsonPath("$.id").isString())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        UUID id = objectMapper.readValue(response,
                io.github.greenapple0101.finaccess.company.CompanyService.RegisteredCompany.class).id();
        try {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT name FROM companies WHERE id = ?", String.class, id)).isEqualTo("등록 테스트 회사");
        } finally {
            companyRepository.deleteById(id);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}"})
    void invalidRegistrationDoesNotStoreCompany(String body) throws Exception {
        long before = companyRepository.count();
        mockMvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Company name must not be blank"));
        assertThat(companyRepository.count()).isEqualTo(before);
    }

    @Test
    void overlongNameIsRejectedWithoutSaving() throws Exception {
        long before = companyRepository.count();
        String body = objectMapper.writeValueAsString(java.util.Map.of("name", "가".repeat(101)));
        mockMvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Company name must not exceed 100 characters"));
        assertThat(companyRepository.count()).isEqualTo(before);
    }

    @Test
    void malformedJsonIsRejectedWithoutSaving() throws Exception {
        long before = companyRepository.count();
        mockMvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        assertThat(companyRepository.count()).isEqualTo(before);
    }

    @Test
    void retrievesCommittedCompanyWithCreationTime() throws Exception {
        Company saved = companyRepository.saveAndFlush(new Company("조회 테스트 회사"));
        try {
            String response = mockMvc.perform(get("/api/companies/{id}", saved.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                    .andExpect(jsonPath("$.name").value("조회 테스트 회사"))
                    .andExpect(jsonPath("$.createdAt").isString())
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            var details = objectMapper.readValue(response,
                    io.github.greenapple0101.finaccess.company.CompanyService.CompanyDetails.class);
            var persistedTime = jdbcTemplate.queryForObject(
                    "SELECT created_at FROM companies WHERE id = ?", java.time.OffsetDateTime.class, saved.getId());
            assertThat(details.createdAt()).isEqualTo(persistedTime.toInstant());
        } finally {
            companyRepository.deleteById(saved.getId());
        }
    }

    @Test
    void missingCompanyReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/companies/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Company not found: " + id));
    }

    @Test
    void malformedCompanyIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/companies/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
