// [이 파일의 역할] 앱이 켜지고, 회사와 계좌 API가 실제 DB까지 연결되는지 자동으로 확인합니다.
// 한 테스트씩 「재료 준비 → 요청 보내기 → 응답과 DB 확인」 순서로 읽으세요.
// @Test는 자동 시험 한 개라는 표시입니다. 시험은 파일에 적힌 순서대로 실행된다고 보장되지 않습니다.
// assertThat(실제값).isEqualTo(기대값)은 두 값이 같은지 확인합니다.
// MockMvc는 브라우저 대신 요청을 보내는 시험 도구입니다. 실제 서버 포트는 열지 않습니다.
// perform은 요청 보내기, andExpect는 결과 확인입니다. $.name은 응답 JSON에서 name을 찾는 표현입니다.
// DB 시험에는 Docker가 필요합니다. Testcontainers가 개발용 DB와 별도의 PostgreSQL을 준비합니다.
// @Transactional이 붙은 시험은 끝날 때 DB 변경을 취소합니다. 이를 '롤백'이라고 합니다.
// 이 표시가 없는 API 시험은 서비스의 저장 확정(커밋)까지 확인하고 데이터를 직접 지웁니다.

package io.github.greenapple0101.finaccess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

// @SpringBootTest는 실제 서비스와 저장소 등 앱의 Spring 구성을 준비합니다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FinaccessApplicationTests {

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
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Flyway flyway;

    // 접속 설정만 읽는 대신, 연결된 DB에 이름을 물어 실제 접속 성공을 확인합니다.
    @Test
    void connectsToPostgresql() {
        // queryForObject는 SQL의 결과 한 개를 읽습니다. String.class는 글자로 받아 달라는 뜻입니다.
        assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
                .isEqualTo(postgres.getDatabaseName());
    }

    // Flyway가 만든 회사 표에 직접 한 줄을 넣고, 이름과 생성 시각이 들어갔는지 확인합니다.
    @Test
    @Transactional
    void migratedCompanyTableStoresData() {
        UUID id = UUID.randomUUID();
        // SQL의 ?는 값이 들어갈 자리입니다. 뒤의 id와 회사명이 그 자리에 순서대로 전달됩니다.
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, ?)", id, "FinAccess Demo");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM companies WHERE id = ?", String.class, id))
                .isEqualTo("FinAccess Demo");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_at IS NOT NULL FROM companies WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    // V1 SQL을 이미 실행했다는 기록이 있고, 다시 실행해도 같은 작업을 반복하지 않는지 확인합니다.
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

    // 저장한 객체를 그대로 보는 데 그치지 않고, DB에서 다시 꺼낸 값이 맞는지 확인합니다.
    @Test
    @Transactional
    void savesAndReloadsCompanyFromDatabase() {
        Company saved = companyRepository.saveAndFlush(new Company("테스트 회사"));
        UUID id = saved.getId();
        assertThat(id).isNotNull();

        // clear는 JPA가 기억하는 객체를 분리합니다. DB의 행을 삭제하는 명령이 아닙니다.
        // 이후 조회가 방금 만든 메모리 객체를 그대로 돌려주는 데 그치지 않게 합니다.
        entityManager.clear();
        Company reloaded = companyRepository.findById(id).orElseThrow();

        assertThat(reloaded).isNotSameAs(saved);
        assertThat(reloaded.getName()).isEqualTo("테스트 회사");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    // 없는 회사 ID를 찾으면 결과가 비어 있어야 합니다. Optional은 결과가 없을 수도 있음을 표현합니다.
    @Test
    @Transactional
    void missingCompanyReturnsEmpty() {
        assertThat(companyRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // 여기의 jwt()는 검증을 마친 관리자 상황을 만드는 시험 도구입니다. 실제 서명 검증은 JwtSecurityTest가 확인합니다.
    // 회사 등록 요청 → 201(생성 성공) 응답 → DB에 저장된 회사 확인 순서입니다.
    // 응답의 id를 꺼내 DB에서 같은 회사를 찾고, finally에서 시험 데이터를 정리합니다.
    @Test
    void registrationCommitsCompanyAndReturnsCreated() throws Exception {
        String response = mockMvc.perform(post("/api/companies").with(admin())
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

    // 이름 누락·null·빈 글·공백만 있는 글을 각각 보냅니다.
    // 400(잘못된 요청)이 나오고, DB의 회사 수도 늘지 않아야 합니다.
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}"})
    void invalidRegistrationDoesNotStoreCompany(String body) throws Exception {
        long before = companyRepository.count();
        mockMvc.perform(post("/api/companies").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Company name must not be blank"));
        assertThat(companyRepository.count()).isEqualTo(before);
    }

    // 최대 100자를 허용하므로, 바로 다음 길이인 101자를 보내 거절되는지 확인합니다.
    @Test
    void overlongNameIsRejectedWithoutSaving() throws Exception {
        long before = companyRepository.count();
        String body = objectMapper.writeValueAsString(java.util.Map.of("name", "가".repeat(101)));
        mockMvc.perform(post("/api/companies").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Company name must not exceed 100 characters"));
        assertThat(companyRepository.count()).isEqualTo(before);
    }

    // 여는 중괄호만 있는 글은 완성된 JSON이 아닙니다. 회사 생성 전에 400으로 거절해야 합니다.
    @Test
    void malformedJsonIsRejectedWithoutSaving() throws Exception {
        long before = companyRepository.count();
        mockMvc.perform(post("/api/companies").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        assertThat(companyRepository.count()).isEqualTo(before);
    }

    // 회사를 먼저 저장한 뒤 GET으로 조회합니다. 생성 시각도 응답과 DB가 같은지 비교합니다.
    @Test
    void retrievesCommittedCompanyWithCreationTime() throws Exception {
        Company saved = companyRepository.saveAndFlush(new Company("조회 테스트 회사"));
        try {
            String response = mockMvc.perform(get("/api/companies/{id}", saved.getId()).with(admin()))
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

    // ID 모양은 맞지만 회사가 없으므로 404(찾을 수 없음)를 기대합니다.
    @Test
    void missingCompanyReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/companies/{id}", id).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Company not found: " + id));
    }

    // not-a-uuid는 ID 형식부터 틀렸으므로 400(잘못된 요청)을 기대합니다.
    @Test
    void malformedCompanyIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/companies/not-a-uuid").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Autowired
    io.github.greenapple0101.finaccess.account.AccountRepository accountRepository;

    // 회사 준비 → 계좌 개설 요청 → DB의 소유 회사와 0원 잔액 확인 순서입니다.
    // 같은 회사가 두 번 개설하면 ID가 다른 계좌 두 개가 생기는지도 확인합니다.
    @Test
    void opensZeroBalanceAccountForCompany() throws Exception {
        Company company = companyRepository.saveAndFlush(new Company("계좌 개설 회사"));
        try {
            String response = mockMvc.perform(post("/api/companies/{companyId}/accounts", company.getId()).with(admin()))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.companyId").value(company.getId().toString()))
                    .andExpect(jsonPath("$.balanceWon").value(0))
                    .andReturn().getResponse().getContentAsString();
            var opened = objectMapper.readValue(response,
                    io.github.greenapple0101.finaccess.account.AccountService.OpenedAccount.class);
            assertThat(opened.id()).isNotNull();
            assertThat(jdbcTemplate.queryForObject("SELECT company_id FROM accounts WHERE id = ?",
                    UUID.class, opened.id())).isEqualTo(company.getId());
            assertThat(jdbcTemplate.queryForObject("SELECT balance_won FROM accounts WHERE id = ?",
                    Long.class, opened.id())).isZero();

            // 본문에 99,999원을 적어 보내도 개설 API는 그 금액을 받지 않습니다. 새 계좌는 언제나 0원입니다.
            String secondResponse = mockMvc.perform(post("/api/companies/{companyId}/accounts", company.getId()).with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"balanceWon\":99999}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.balanceWon").value(0))
                    .andReturn().getResponse().getContentAsString();
            var second = objectMapper.readValue(secondResponse,
                    io.github.greenapple0101.finaccess.account.AccountService.OpenedAccount.class);
            assertThat(second.id()).isNotEqualTo(opened.id());
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM accounts WHERE company_id = ?",
                    Long.class, company.getId())).isEqualTo(2L);
        } finally {
            // 계좌가 회사를 참조하므로 계좌부터 지워야 합니다. finally는 확인 중 오류가 나도 정리를 실행합니다.
            jdbcTemplate.update("DELETE FROM accounts WHERE company_id = ?", company.getId());
            companyRepository.deleteById(company.getId());
        }
    }

    // 없는 회사에 계좌를 만들면 404가 나오고 계좌 수는 그대로여야 합니다.
    @Test
    void missingCompanyCannotOpenAccount() throws Exception {
        long before = accountRepository.count();
        UUID missing = UUID.randomUUID();
        mockMvc.perform(post("/api/companies/{companyId}/accounts", missing).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Company not found: " + missing));
        assertThat(accountRepository.count()).isEqualTo(before);
    }

    // 회사 ID 형식이 틀리면 400이 나오고 계좌 수는 그대로여야 합니다.
    @Test
    void invalidCompanyIdCannotOpenAccount() throws Exception {
        long before = accountRepository.count();
        mockMvc.perform(post("/api/companies/not-a-uuid/accounts").with(admin()))
                .andExpect(status().isBadRequest());
        assertThat(accountRepository.count()).isEqualTo(before);
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"));
    }
}
