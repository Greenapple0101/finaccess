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

// 전체 Spring 설정과 실제 테스트용 PostgreSQL을 함께 사용하는 통합 테스트입니다.
// MockMvc는 HTTP 요청 처리 과정을 실행하지만 실제 네트워크 포트를 열지는 않습니다.
// 테스트 이름은 확인하려는 행동을 표현합니다. 실패하면 해당 행동이 보장되지 않는다는 신호입니다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FinaccessApplicationTests {

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
    @Autowired
    JdbcTemplate jdbcTemplate;

    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired
    Flyway flyway;

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // SELECT로 현재 DB 이름을 확인합니다. 설정 파일만 검사하는 대신 실제 연결 성공을 검증합니다.
    void connectsToPostgresql() {
        assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
                .isEqualTo(postgres.getDatabaseName());
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    @Transactional
    // Flyway가 만든 테이블에 SQL로 저장하고 조회합니다. 테스트 트랜잭션은 종료 시 롤백됩니다.
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

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // V1 적용 이력을 확인하고 Flyway를 다시 호출해 중복 실행 건수가 0인지 확인합니다.
    void migrationIsRecordedAndNotAppliedTwice() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class))
                .isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired
    CompanyRepository companyRepository;

    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired
    EntityManager entityManager;

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    @Transactional
    // saveAndFlush는 INSERT를 DB에 반영합니다. flush는 commit과 다릅니다.
    // clear로 JPA 메모리 상태를 비운 다음 다시 조회하여 실제 DB 왕복을 검증합니다.
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

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    @Transactional
    // 없는 UUID로 조회했을 때 null 대신 Optional.empty가 반환되는지 확인합니다.
    void missingCompanyReturnsEmpty() {
        assertThat(companyRepository.findById(UUID.randomUUID())).isEmpty();
    }

    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired
    MockMvc mockMvc;

    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired
    ObjectMapper objectMapper;

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 테스트 Transactional을 붙이지 않아 Service 자체의 커밋을 확인합니다.
    // POST 응답에서 UUID를 꺼내 JDBC로 저장 결과를 조회하고 finally에서 테스트 데이터를 정리합니다.
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
    // 각 입력값마다 같은 테스트를 반복합니다. 누락·null·빈 이름·공백을 확인합니다.
    // 400 응답뿐 아니라 요청 전후 DB 행 개수도 같아야 합니다.
    void invalidRegistrationDoesNotStoreCompany(String body) throws Exception {
        long before = companyRepository.count();
        mockMvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Company name must not be blank"));
        assertThat(companyRepository.count()).isEqualTo(before);
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 101자 입력이 400으로 거부되고 DB에 저장되지 않는지 확인합니다.
    void overlongNameIsRejectedWithoutSaving() throws Exception {
        long before = companyRepository.count();
        String body = objectMapper.writeValueAsString(java.util.Map.of("name", "가".repeat(101)));
        mockMvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Company name must not exceed 100 characters"));
        assertThat(companyRepository.count()).isEqualTo(before);
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 완성되지 않은 JSON이 역직렬화 단계에서 400으로 거부되는지 확인합니다.
    void malformedJsonIsRejectedWithoutSaving() throws Exception {
        long before = companyRepository.count();
        mockMvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        assertThat(companyRepository.count()).isEqualTo(before);
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 먼저 회사를 커밋하고 GET으로 조회합니다. 응답의 생성 시각도 DB 값과 비교합니다.
    // try-finally는 검증이 실패하더라도 정리 코드가 실행되게 합니다.
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

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 형식은 맞지만 존재하지 않는 UUID이므로 404와 오류 상세 내용을 기대합니다.
    void missingCompanyReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/companies/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Company not found: " + id));
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // UUID로 바꿀 수 없는 문자열은 데이터 없음이 아니라 요청 형식 오류이므로 400을 기대합니다.
    void malformedCompanyIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/companies/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
