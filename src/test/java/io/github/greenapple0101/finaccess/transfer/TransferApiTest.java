package io.github.greenapple0101.finaccess.transfer;

import io.github.greenapple0101.finaccess.account.Account;
import io.github.greenapple0101.finaccess.account.AccountRepository;
import io.github.greenapple0101.finaccess.user.AppUser;
import io.github.greenapple0101.finaccess.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.greenapple0101.finaccess.company.Company;
import io.github.greenapple0101.finaccess.company.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 이체 요청을 별도 PostgreSQL에 저장하고 재조회합니다. 개발용 DB와 분리됩니다.
// 테스트에 Transactional을 붙이지 않아 서비스가 시작한 트랜잭션의 실제 commit을 확인합니다.
// 제약 위반 테스트는 예외를 예상하는 테스트이며 실패 로그가 보여도 의도한 결과일 수 있습니다.
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Testcontainers
class TransferApiTest {

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
    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired JdbcTemplate jdbc;

    @Autowired TransferRequestRepository transfers;
    @Autowired AppUserRepository users;
    private UUID sourceId;
    private UUID destinationId;
    private UUID requesterId;

    @Autowired TransferRequestService service;
    private UUID otherCompanyAccountId;

    @BeforeEach
    void prepareReferences() {
        Company company = companies.saveAndFlush(new Company("요청 회사"));
        Company other = companies.saveAndFlush(new Company("수취 회사"));
        sourceId = accounts.saveAndFlush(new Account(company)).getId();
        destinationId = accounts.saveAndFlush(new Account(company)).getId();
        otherCompanyAccountId = accounts.saveAndFlush(new Account(other)).getId();
        requesterId = users.saveAndFlush(new AppUser(company, "https://identity.example", "requester")).getId();
    }

    // 실제 커밋을 검사하므로 자동 롤백 대신 테스트 전용 DB를 직접 정리합니다.
    // 외래키로 참조하는 자식 테이블부터 삭제합니다. 개발 DB에는 연결하지 않습니다.
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM transfer_requests");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM app_users");
        jdbc.update("DELETE FROM companies");
    }

    @Autowired MockMvc mvc;
    @Autowired tools.jackson.databind.ObjectMapper mapper;

    @Autowired TransferWorkflowService workflow;

    // 잔액 준비는 테스트 전용 SQL입니다. 운영 입금 API나 실제 자금 충전 기능이 아닙니다.
    private void balance(UUID account, long amount) {
        jdbc.update("UPDATE accounts SET balance_won = ? WHERE id = ?", amount, account);
    }

    private void assertBalances(long source, long destination) {
        assertThat(accounts.findById(sourceId).orElseThrow().getBalanceWon()).isEqualTo(source);
        assertThat(accounts.findById(destinationId).orElseThrow().getBalanceWon()).isEqualTo(destination);
    }

    private void assertApprovedWithoutCompletion(UUID id) {
        var loaded = transfers.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TransferStatus.APPROVED);
        assertThat(loaded.getReviewerId()).isNotNull();
        assertThat(loaded.getReviewedAt()).isNotNull();
        assertThat(loaded.getExecutedBy()).isNull();
        assertThat(loaded.getCompletedAt()).isNull();
    }

    @Test
    void fullHttpFlowMovesMoneyOnce() throws Exception {
        balance(sourceId, 1500L);
        balance(destinationId, 200L);
        // HTTP로 생성된 ID를 JSON에서 읽어 승인·실행 경로에 사용합니다.
        String json = mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(body(sourceId, destinationId, "1000", requesterId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = mapper.readValue(json, TransferRequestService.RequestedTransfer.class).id();
        UUID actor = reviewer();
        mvc.perform(post("/api/transfers/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + actor + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/transfers/" + id + "/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + actor + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.executedBy").value(actor.toString()))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        assertBalances(500L, 1200L);
        var loaded = transfers.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(loaded.getExecutedBy()).isEqualTo(actor);
        assertThat(loaded.getCompletedAt()).isNotNull();
        // 재실행은 409로 거부하여 이중 출금을 막습니다. 요청 생성의 멱등키와는 별개입니다.
        mvc.perform(post("/api/transfers/" + id + "/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + actor + "\"}"))
                .andExpect(status().isConflict());
        assertBalances(500L, 1200L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refusesRequestedOrRejectedExecution(boolean reject) throws Exception {
        UUID id = newRequest();
        UUID actor = reviewer();
        if (reject) workflow.reject(id, actor);
        mvc.perform(post("/api/transfers/" + id + "/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + actor + "\"}"))
                .andExpect(status().isConflict());
        assertBalances(0L, 0L);
        assertThat(transfers.findById(id).orElseThrow().getCompletedAt()).isNull();
    }

    @Test
    void insufficientFundsLeaveApprovalAndBalancesUnchanged() throws Exception {
        UUID id = newRequest();
        UUID actor = reviewer();
        workflow.approve(id, actor);
        balance(sourceId, 999L);
        mvc.perform(post("/api/transfers/" + id + "/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + actor + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertBalances(999L, 0L);
        assertApprovedWithoutCompletion(id);
    }

    @Test
    void destinationOverflowRollsBackAlreadyFlushedWithdrawal() throws Exception {
        UUID id = newRequest();
        UUID actor = reviewer();
        workflow.approve(id, actor);
        balance(sourceId, 1000L);
        balance(destinationId, Long.MAX_VALUE);
        // 출금 UPDATE의 flush 후 입금 계산이 실패합니다. API 종료 뒤 별도 조회로 DB를 확인합니다.
        mvc.perform(post("/api/transfers/" + id + "/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + actor + "\"}"))
                .andExpect(status().isConflict());
        assertBalances(1000L, Long.MAX_VALUE);
        assertApprovedWithoutCompletion(id);
    }

    @Test
    void completionWriteFailureRollsBackBothAccounts() {
        UUID id = newRequest();
        UUID actor = reviewer();
        workflow.approve(id, actor);
        balance(sourceId, 1500L);
        balance(destinationId, 200L);
        // 테스트 DB에만 실패 조건을 주입합니다. COMPLETED 저장이 DB에서 거부되게 합니다.
        // 본문이 정상 반환하려 해도 commit 시점의 실패가 출금·입금을 함께 롤백하는지 확인합니다.
        jdbc.execute("ALTER TABLE transfer_requests ADD CONSTRAINT ck_test_no_completion CHECK (status <> 'COMPLETED')");
        try {
            assertThatThrownBy(() -> workflow.execute(id, actor))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertBalances(1500L, 200L);
            assertApprovedWithoutCompletion(id);
        } finally {
            // 다른 테스트에 영향을 주지 않도록 임시 제약을 반드시 제거합니다.
            jdbc.execute("ALTER TABLE transfer_requests DROP CONSTRAINT ck_test_no_completion");
        }
    }

    @Test
    void executionValidatesActorAndReference() throws Exception {
        UUID id = newRequest();
        workflow.approve(id, reviewer());
        balance(sourceId, 1000L);
        mvc.perform(post("/api/transfers/" + id + "/execute").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/transfers/" + id + "/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + outsider() + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/transfers/" + id + "/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/transfers/" + UUID.randomUUID() + "/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + requesterId + "\"}"))
                .andExpect(status().isNotFound());
        assertBalances(1000L, 0L);
        assertApprovedWithoutCompletion(id);
    }

    @Test
    void executesToOtherCompanyAndAllowsEntireBalance() {
        UUID actor = reviewer();
        UUID id = service.request(sourceId, otherCompanyAccountId, 1000L, requesterId).id();
        workflow.approve(id, actor);
        balance(sourceId, 1000L);
        workflow.execute(id, actor);
        assertThat(accounts.findById(sourceId).orElseThrow().getBalanceWon()).isZero();
        assertThat(accounts.findById(otherCompanyAccountId).orElseThrow().getBalanceWon()).isEqualTo(1000L);
    }

    // 두 스레드가 각각 Spring 서비스를 호출하여 독립된 DB 트랜잭션을 시작합니다.
    // 카운트다운으로 시작을 맞추고 모든 대기에 제한 시간을 두어 무한 대기를 피합니다.
    private java.util.List<String> race(Runnable first, Runnable second) throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (Runnable action : new Runnable[]{first, second}) {
                jobs.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
                    try {
                        action.run();
                        return "success";
                    } catch (TransferStateConflictException | io.github.greenapple0101.finaccess.account.InsufficientBalanceException error) {
                        return error.getClass().getSimpleName();
                    }
                }));
            }
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = new java.util.ArrayList<String>();
            for (var job : jobs) results.add(job.get(20, java.util.concurrent.TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void simultaneousReviewHasOnlyOneWinner() throws Exception {
        UUID id = newRequest();
        UUID actor = reviewer();
        assertThat(race(() -> workflow.approve(id, actor), () -> workflow.reject(id, actor)))
                .containsExactlyInAnyOrder("success", "TransferStateConflictException");
        var loaded = transfers.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isIn(TransferStatus.APPROVED, TransferStatus.REJECTED);
        assertThat(loaded.getReviewerId()).isEqualTo(actor);
    }

    @Test
    void simultaneousSameRequestExecutesOnce() throws Exception {
        UUID id = newRequest();
        UUID actor = reviewer();
        workflow.approve(id, actor);
        balance(sourceId, 2000L);
        assertThat(race(() -> workflow.execute(id, actor), () -> workflow.execute(id, actor)))
                .containsExactlyInAnyOrder("success", "TransferStateConflictException");
        assertBalances(1000L, 1000L);
        assertThat(transfers.findById(id).orElseThrow().getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    void competingRequestsCannotOverdrawSharedAccount() throws Exception {
        UUID first = newRequest();
        UUID second = newRequest();
        UUID actor = reviewer();
        workflow.approve(first, actor);
        workflow.approve(second, actor);
        balance(sourceId, 1500L);
        assertThat(race(() -> workflow.execute(first, actor), () -> workflow.execute(second, actor)))
                .containsExactlyInAnyOrder("success", "InsufficientBalanceException");
        assertBalances(500L, 1000L);
        assertThat(transfers.findAll().stream().map(TransferRequest::getStatus).toList())
                .containsExactlyInAnyOrder(TransferStatus.COMPLETED, TransferStatus.APPROVED);
    }

    @Test
    void oppositeDirectionTransfersUseConsistentLockOrder() throws Exception {
        UUID first = service.request(sourceId, destinationId, 250L, requesterId).id();
        UUID second = service.request(destinationId, sourceId, 500L, requesterId).id();
        UUID actor = reviewer();
        workflow.approve(first, actor);
        workflow.approve(second, actor);
        balance(sourceId, 1000L);
        balance(destinationId, 1000L);
        assertThat(race(() -> workflow.execute(first, actor), () -> workflow.execute(second, actor)))
                .containsExactly("success", "success");
        assertBalances(1250L, 750L);
    }

    // 같은 회사의 별도 결재자와 다른 회사 사용자를 만들어 허용/거부 경계를 확인합니다.
    private UUID reviewer() {
        var company = companies.findById(accounts.findById(sourceId).orElseThrow().getCompany().getId()).orElseThrow();
        return users.saveAndFlush(new AppUser(company, "https://identity.example", "reviewer")).getId();
    }

    private UUID outsider() {
        var company = companies.findById(accounts.findById(otherCompanyAccountId).orElseThrow().getCompany().getId()).orElseThrow();
        return users.saveAndFlush(new AppUser(company, "https://identity.example", "outsider")).getId();
    }

    private UUID newRequest() {
        return service.request(sourceId, destinationId, 1000L, requesterId).id();
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void reviewsAndPersistsActorAndTime(String action) throws Exception {
        UUID id = newRequest();
        UUID actor = reviewer();
        String expected = action.equals("approve") ? "APPROVED" : "REJECTED";
        mvc.perform(post("/api/transfers/" + id + "/" + action).contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + actor + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.reviewerId").value(actor.toString()))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
        var loaded = transfers.findById(id).orElseThrow();
        assertThat(loaded.getStatus().name()).isEqualTo(expected);
        assertThat(loaded.getReviewerId()).isEqualTo(actor);
        assertThat(loaded.getReviewedAt()).isNotNull();
        assertThat(accounts.findById(sourceId).orElseThrow().getBalanceWon()).isZero();
        // 동일 행동의 반복과 반대 결재 모두 409입니다. 최초 처리자·시각은 유지됩니다.
        for (String again : new String[]{"approve", "reject"}) {
            mvc.perform(post("/api/transfers/" + id + "/" + again).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"actorId\":\"" + actor + "\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        }
        assertThat(transfers.findById(id).orElseThrow().getReviewedAt()).isEqualTo(loaded.getReviewedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void rejectsSelfAndOtherCompanyReview(String action) throws Exception {
        UUID id = newRequest();
        for (UUID actor : new UUID[]{requesterId, outsider()}) {
            mvc.perform(post("/api/transfers/" + id + "/" + action).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"actorId\":\"" + actor + "\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        }
        var loaded = transfers.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(loaded.getReviewerId()).isNull();
        assertThat(loaded.getReviewedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void rejectsMissingReviewActorAndTransfer(String action) throws Exception {
        UUID id = newRequest();
        mvc.perform(post("/api/transfers/" + id + "/" + action).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/transfers/" + id + "/" + action).contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/transfers/" + UUID.randomUUID() + "/" + action).contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + reviewer() + "\"}"))
                .andExpect(status().isNotFound());
        assertThat(transfers.findById(id).orElseThrow().getStatus()).isEqualTo(TransferStatus.REQUESTED);
    }

    // MockMvc는 서버 포트를 열지 않고 실제 Controller와 Service, DB까지 실행합니다.
    @Test
    void createsRequestAndCommits() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(body(sourceId, destinationId, "1000", requesterId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.amountWon").value(1000))
                .andExpect(jsonPath("$.sourceAccountId").value(sourceId.toString()))
                .andExpect(jsonPath("$.destinationAccountId").value(destinationId.toString()))
                .andExpect(jsonPath("$.requesterId").value(requesterId.toString()));
        assertThat(transfers.count()).isEqualTo(1);
        assertThat(accounts.findById(sourceId).orElseThrow().getBalanceWon()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "null", "1.5", "9223372036854775808"})
    void rejectsInvalidAmounts(String amount) throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(body(sourceId, destinationId, amount, requesterId)))
                .andExpect(status().isBadRequest());
        assertThat(transfers.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "null", "", "{\"sourceAccountId\":\"not-a-uuid\"}"})
    void rejectsMissingOrMalformedBody(String json) throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        assertThat(transfers.count()).isZero();
    }

    @Test
    void rejectsSameAccount() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(body(sourceId, sourceId, "1000", requesterId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertThat(transfers.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "destination", "requester"})
    void missingReferenceReturns404(String missing) throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(body(missing.equals("source") ? UUID.randomUUID() : sourceId,
                        missing.equals("destination") ? UUID.randomUUID() : destinationId, "1000",
                        missing.equals("requester") ? UUID.randomUUID() : requesterId)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        assertThat(transfers.count()).isZero();
    }

    @Test
    void companyMismatchReturns403() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(body(otherCompanyAccountId, destinationId, "1000", requesterId)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        assertThat(transfers.count()).isZero();
    }

    // 테스트용 JSON입니다. UUID와 테스트 상수만 사용하며 실제 사용자 입력을 문자열로 조립하지 않습니다.
    private String body(UUID source, UUID destination, String amount, UUID requester) {
        return """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amountWon":%s,"requesterId":"%s"}
                """.formatted(source, destination, amount, requester);
    }
}
