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
// [이 파일은 통합 테스트]
// @SpringBootTest는 앱의 Spring 구성을 준비합니다. 단순히 메서드만 new로 호출하는 것보다 넓은 검증입니다.
// @Testcontainers와 @Container는 별도 PostgreSQL 컨테이너의 시작·종료를 JUnit과 연결합니다.
// 개발용 Compose DB 대신 새 DB를 사용하므로 Docker가 필요합니다.
// static final 컨테이너 필드는 이 테스트 클래스에서 공유할 참조를 뜻합니다.
// @DynamicPropertySource는 컨테이너의 접속 정보를 Spring 설정으로 제공합니다.
// postgres::getJdbcUrl의 ::는 메서드 참조입니다. 지금 문자열을 넘기는 대신 값을 제공할 함수를 넘깁니다.
// Spring이 설정값을 필요로 할 때 이 함수를 호출할 수 있습니다.
// @Autowired는 Spring이 준비한 Bean을 테스트 필드에 넣으라는 표시입니다.
// 운영 Service는 생성자 주입을 사용하고, 테스트는 간편하게 필드 주입을 사용한 것입니다.
//
// EntityManager는 JPA 엔티티를 관리하는 인터페이스입니다.
// flush는 변경한 SQL을 DB에 반영하고, clear는 관리 중인 객체를 영속성 컨텍스트에서 분리합니다.
// flush 뒤 clear를 해야 이후 조회가 메모리 객체 재사용만으로 끝나는 일을 피할 수 있습니다.
// JdbcTemplate은 SQL 실행과 결과 읽기를 도와줍니다. JPA 결과를 SQL로 독립 확인할 때 사용합니다.
// 테스트의 @Transactional은 기본적으로 종료 시 롤백합니다.
// @Transactional 없는 테스트에서는 Service/Repository가 커밋한 값을 확인하고 직접 정리합니다.
//
// [HTTP 테스트 문법]
// MockMvc는 실제 네트워크 포트를 열지 않고 Spring MVC 요청 처리를 실행합니다.
// perform(post/get(...))는 요청 실행, andExpect(...)는 응답 검증입니다.
// contentType은 보내는 본문의 형식, content는 보내는 문자열입니다.
// 응답 contentTypeCompatibleWith는 응답의 미디어 타입이 JSON 등에 맞는지 확인합니다.
// jsonPath("$.name")에서 $는 JSON의 최상위 객체, .name은 그 객체의 name 필드입니다.
// andReturn은 결과를 가져오며 getResponse/getContentAsString으로 본문을 읽을 수 있습니다.
// ObjectMapper.readValue는 JSON을 지정한 Java 타입으로 변환합니다.
// ClassName.class는 그 타입 정보를 넘기는 표현입니다. new로 객체를 만드는 것과 다릅니다.
// throws Exception은 예외가 밖으로 전달될 수 있다는 메서드 선언이며 예외를 무시하는 명령은 아닙니다.
// try/finally는 검증 중 실패하더라도 finally의 데이터 정리를 실행하기 위해 사용합니다.
//
// @ParameterizedTest는 같은 검증을 여러 입력에 반복합니다.
// @ValueSource는 반복할 문자열·숫자들을 제공하고, @NullAndEmptySource는 null·빈 값을 제공합니다.
// 예를 들어 0, -1, Long.MIN_VALUE를 지정하면 각각 별도의 테스트 실행으로 집계됩니다.
// Long.MAX_VALUE/MIN_VALUE는 long 표현 범위의 끝값이며 실제 서비스 거래 한도는 아닙니다.

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
        // queryForObject는 단일 결과를 읽습니다. SQL의 ?에는 뒤 인자가 바인딩됩니다.
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

        // queryForObject는 단일 결과를 읽습니다. SQL의 ?에는 뒤 인자가 바인딩됩니다.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM companies WHERE id = ?", String.class, id))
                .isEqualTo("FinAccess Demo");
        // queryForObject는 단일 결과를 읽습니다. SQL의 ?에는 뒤 인자가 바인딩됩니다.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_at IS NOT NULL FROM companies WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // V1 적용 이력을 확인하고 Flyway를 다시 호출해 중복 실행 건수가 0인지 확인합니다.
    void migrationIsRecordedAndNotAppliedTwice() {
        // queryForObject는 단일 결과를 읽습니다. SQL의 ?에는 뒤 인자가 바인딩됩니다.
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
        // 메모리의 관리 객체를 분리합니다. DB 행을 삭제하는 명령은 아닙니다.
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
        // queryForObject는 단일 결과를 읽습니다. SQL의 ?에는 뒤 인자가 바인딩됩니다.
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
        // queryForObject는 단일 결과를 읽습니다. SQL의 ?에는 뒤 인자가 바인딩됩니다.
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

    @Autowired
    io.github.greenapple0101.finaccess.account.AccountRepository accountRepository;

    // [만든 순서 3] API 응답뿐 아니라 커밋된 계좌의 회사와 잔액도 SQL로 확인합니다.
    // 테스트 트랜잭션을 사용하지 않아 Service 자체의 커밋이 완료되어야 조회됩니다.
    @Test
    void opensZeroBalanceAccountForCompany() throws Exception {
        Company company = companyRepository.saveAndFlush(new Company("계좌 개설 회사"));
        try {
            String response = mockMvc.perform(post("/api/companies/{companyId}/accounts", company.getId()))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.companyId").value(company.getId().toString()))
                    .andExpect(jsonPath("$.balanceWon").value(0))
                    .andReturn().getResponse().getContentAsString();
            var opened = objectMapper.readValue(response,
                    io.github.greenapple0101.finaccess.account.AccountService.OpenedAccount.class);
            assertThat(opened.id()).isNotNull();
        // queryForObject는 단일 결과를 읽습니다. SQL의 ?에는 뒤 인자가 바인딩됩니다.
            assertThat(jdbcTemplate.queryForObject("SELECT company_id FROM accounts WHERE id = ?",
                    UUID.class, opened.id())).isEqualTo(company.getId());
        // queryForObject는 단일 결과를 읽습니다. SQL의 ?에는 뒤 인자가 바인딩됩니다.
            assertThat(jdbcTemplate.queryForObject("SELECT balance_won FROM accounts WHERE id = ?",
                    Long.class, opened.id())).isZero();

            // 잔액을 본문에 적더라도 API는 이를 입력으로 사용하지 않습니다.
            // 두 번째 개설 요청은 별도 계좌를 만들며 0원으로 시작합니다.
            String secondResponse = mockMvc.perform(post("/api/companies/{companyId}/accounts", company.getId())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"balanceWon\":99999}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.balanceWon").value(0))
                    .andReturn().getResponse().getContentAsString();
            var second = objectMapper.readValue(secondResponse,
                    io.github.greenapple0101.finaccess.account.AccountService.OpenedAccount.class);
            assertThat(second.id()).isNotEqualTo(opened.id());
        // queryForObject는 단일 결과를 읽습니다. SQL의 ?에는 뒤 인자가 바인딩됩니다.
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM accounts WHERE company_id = ?",
                    Long.class, company.getId())).isEqualTo(2L);
        } finally {
            // 외래키 때문에 계좌를 먼저 제거하고 회사를 정리합니다.
            jdbcTemplate.update("DELETE FROM accounts WHERE company_id = ?", company.getId());
            companyRepository.deleteById(company.getId());
        }
    }

    @Test
    void missingCompanyCannotOpenAccount() throws Exception {
        long before = accountRepository.count();
        UUID missing = UUID.randomUUID();
        mockMvc.perform(post("/api/companies/{companyId}/accounts", missing))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Company not found: " + missing));
        assertThat(accountRepository.count()).isEqualTo(before);
    }

    @Test
    void invalidCompanyIdCannotOpenAccount() throws Exception {
        long before = accountRepository.count();
        mockMvc.perform(post("/api/companies/not-a-uuid/accounts"))
                .andExpect(status().isBadRequest());
        assertThat(accountRepository.count()).isEqualTo(before);
    }
}
