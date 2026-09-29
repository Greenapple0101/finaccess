// [이 파일의 역할] 요청 접수 → 승인/반려 → 모의 이체가 API와 DB를 통해 올바르게 작동하는지 확인합니다.
// 먼저 fullHttpFlowMovesMoneyOnce에서 정상 흐름을 읽고, 실패·동시 실행 시험을 읽으세요.
// 각 시험은 「계좌와 잔액 준비 → 동작 실행 → 응답과 DB 확인」 순서입니다.
// MockMvc는 브라우저 대신 요청을 보내는 도구입니다. 서버 포트 없이 실제 Controller·Service까지 실행합니다.
// perform은 요청 보내기, andExpect는 응답 확인입니다. $.status는 응답 JSON의 status 값을 가리킵니다.
// Spring과 테스트 전용 PostgreSQL을 함께 켜므로 Docker가 필요합니다. 개발용 DB는 사용하지 않습니다.
// 이 클래스에는 @Transactional을 붙이지 않았습니다. 서비스의 저장 확정(커밋)을 끝까지 확인하려는 것입니다.
// 실패 뒤 별도로 DB를 조회하여, 변경을 되돌리는 '롤백'이 실제로 되었는지도 확인합니다.
// actorId는 시험에서 전달하는 사용자 ID입니다. 이 시험이 로그인 인증이나 역할 검증까지 보장하지는 않습니다.

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

// @SpringBootTest는 실제 서비스와 저장소 등 앱의 Spring 구성을 준비합니다.
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Testcontainers
class TransferApiTest {

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
    @Autowired AccountRepository accounts;
    @Autowired CompanyRepository companies;
    @Autowired JdbcTemplate jdbc;

    @Autowired TransferRequestRepository transfers;
    @Autowired AppUserRepository users;
    private UUID sourceId;
    private UUID destinationId;
    private UUID requesterId;

    @Autowired TransferRequestService service;
    private UUID otherCompanyAccountId;

    // @BeforeEach 때문에 시험마다 회사·계좌·요청자를 새로 준비합니다.
    // source는 출금, destination은 입금 계좌입니다. 다른 회사 계좌도 만들어 허용 범위를 검사합니다.
    @BeforeEach
    void prepareReferences() {
        Company company = companies.saveAndFlush(new Company("요청 회사"));
        Company other = companies.saveAndFlush(new Company("수취 회사"));
        sourceId = accounts.saveAndFlush(new Account(company)).getId();
        destinationId = accounts.saveAndFlush(new Account(company)).getId();
        otherCompanyAccountId = accounts.saveAndFlush(new Account(other)).getId();
        requesterId = users.saveAndFlush(new AppUser(company, "https://identity.example", "requester")).getId();
    }

    // @AfterEach는 각 시험이 끝난 뒤 정리할 일을 표시합니다. 여기서는 커밋된 시험 데이터를 지웁니다.
    // 요청서가 계좌를 참조하므로 요청서부터 지웁니다. 이런 연결 규칙을 외래키라고 합니다.
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

    // 시험 시작 잔액을 SQL로 맞추는 도우미입니다. 실제 충전 기능이나 입금 API가 아닙니다.
    private void balance(UUID account, long amount) {
        jdbc.update("UPDATE accounts SET balance_won = ? WHERE id = ?", amount, account);
    }

    // DB에서 두 계좌를 다시 읽어 예상 잔액과 비교합니다. 응답에 성공이라고만 적혔는지 보는 데 그치지 않습니다.
    private void assertBalances(long source, long destination) {
        assertThat(accounts.findById(sourceId).orElseThrow().getBalanceWon()).isEqualTo(source);
        assertThat(accounts.findById(destinationId).orElseThrow().getBalanceWon()).isEqualTo(destination);
    }

    // 실행이 실패한 요청은 승인 상태로 남고, 결재 기록은 유지되며, 실행자·완료 시각은 없어야 합니다.
    private void assertApprovedWithoutCompletion(UUID id) {
        var loaded = transfers.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TransferStatus.APPROVED);
        assertThat(loaded.getReviewerId()).isNotNull();
        assertThat(loaded.getReviewedAt()).isNotNull();
        assertThat(loaded.getExecutedBy()).isNull();
        assertThat(loaded.getCompletedAt()).isNull();
    }

    // 출금 1,500원·입금 200원 준비 → 1,000원 요청 → 승인 → 실행 → 500원·1,200원 확인 순서입니다.
    // 같은 이체를 다시 실행하면 409(현재 상태와 충돌)로 거절하고, 돈이 두 번 나가지 않아야 합니다.
    @Test
    void fullHttpFlowMovesMoneyOnce() throws Exception {
        balance(sourceId, 1500L);
        balance(destinationId, 200L);
        String json = mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(body(sourceId, destinationId, "1000", requesterId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        // 응답 JSON 글을 Java 결과 객체로 바꾸고, 방금 생성된 요청 ID를 꺼내 다음 URL에 씁니다.
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
        mvc.perform(post("/api/transfers/" + id + "/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + actor + "\"}"))
                .andExpect(status().isConflict());
        assertBalances(500L, 1200L);
    }

    // false일 때는 요청됨, true일 때는 반려됨 상태로 시험합니다. 둘 다 승인된 요청이 아니므로 실행 불가입니다.
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

    // 1,000원을 보내려는데 999원만 있으면 실패해야 합니다. 돈과 승인 기록이 그대로인지 확인합니다.
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

    // 입금 계좌 잔액을 long의 최대값으로 만들어 1원도 더 못 받게 합니다.
    // 서비스는 출금 SQL을 이미 DB에 보낸 뒤 입금 계산에 실패합니다.
    // DB로 보낸 것(flush)과 저장 확정(commit)은 다릅니다. 아직 확정 전이므로 출금도 취소되어야 합니다.
    @Test
    void destinationOverflowRollsBackAlreadyFlushedWithdrawal() throws Exception {
        UUID id = newRequest();
        UUID actor = reviewer();
        workflow.approve(id, actor);
        balance(sourceId, 1000L);
        balance(destinationId, Long.MAX_VALUE);
        mvc.perform(post("/api/transfers/" + id + "/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"" + actor + "\"}"))
                .andExpect(status().isConflict());
        assertBalances(1000L, Long.MAX_VALUE);
        assertApprovedWithoutCompletion(id);
    }

    // 계좌 계산이 끝났어도, 마지막 완료 기록을 저장하지 못하면 전부 취소되어야 합니다.
    // 이 시험은 테스트 DB에만 "COMPLETED 저장 금지" 규칙을 잠깐 넣어 마지막 저장을 일부러 실패시킵니다.
    @Test
    void completionWriteFailureRollsBackBothAccounts() {
        UUID id = newRequest();
        UUID actor = reviewer();
        workflow.approve(id, actor);
        balance(sourceId, 1500L);
        balance(destinationId, 200L);
        // commit 과정에서 완료 상태 저장이 거절되도록 임시 DB 규칙을 추가합니다.
        jdbc.execute("ALTER TABLE transfer_requests ADD CONSTRAINT ck_test_no_completion CHECK (status <> 'COMPLETED')");
        try {
            assertThatThrownBy(() -> workflow.execute(id, actor))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertBalances(1500L, 200L);
            assertApprovedWithoutCompletion(id);
        } finally {
            // finally는 시험 확인이 실패해도 실행됩니다. 임시 규칙을 제거해 다른 시험에 영향을 주지 않습니다.
            jdbc.execute("ALTER TABLE transfer_requests DROP CONSTRAINT ck_test_no_completion");
        }
    }

    // 실행자 누락은 400, 다른 회사 사용자는 403, 없는 사용자나 요청은 404인지 확인합니다.
    // 어느 경우든 잔액과 승인 상태가 바뀌면 안 됩니다.
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

    // 다른 회사로 잔액 전부인 1,000원을 보내면 출금 계좌는 0원, 수취 계좌는 1,000원이 되어야 합니다.
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

    // 동작 두 개를 거의 동시에 시작해 서로 영향을 주는 상황을 만드는 시험 도우미입니다.
    // 스레드는 따로 실행되는 작업 흐름입니다. 두 스레드가 서비스를 각각 호출해 별도 DB 트랜잭션을 시작합니다.
    // ready는 둘 다 준비됐다는 신호, start는 함께 출발하라는 신호입니다.
    // 출발을 맞춰도 DB의 같은 줄에 정확히 동시에 도착한다고 보장되지는 않습니다.
    private java.util.List<String> race(Runnable first, Runnable second) throws Exception {
        // 동시에 일할 작업자 두 명을 준비합니다. 작업자는 별도 스레드에서 코드를 실행합니다.
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (Runnable action : new Runnable[]{first, second}) {
                // submit은 작업자에게 동작을 맡깁니다. Future는 나중에 완료 결과를 받을 때 쓰는 보관함입니다.
                jobs.add(pool.submit(() -> {
                    // 준비된 작업자 수를 표시한 뒤, 공동 출발 신호를 기다립니다. 10초가 지나도 신호가 없으면 실패합니다.
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
            // 두 작업자가 준비됐으면 출발 신호를 보냅니다. 어떤 작업이 먼저 끝날지는 정하지 않습니다.
            start.countDown();
            var results = new java.util.ArrayList<String>();
            // 각 결과를 최대 20초 기다립니다. 잠금 문제로 끝없이 기다리는 시험을 방지합니다.
            for (var job : jobs) results.add(job.get(20, java.util.concurrent.TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            // 오류가 나도 작업자들을 종료하고, 종료까지 끝났는지 확인합니다.
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    // 같은 요청을 승인하는 작업과 반려하는 작업이 경쟁하면 하나만 성공해야 합니다.
    // 먼저 처리된 상태가 남고, 나중 작업은 이미 결재됐다는 오류를 받아야 합니다.
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

    // 동시에 같은 1,000원 이체를 실행해도 한 번만 성공해야 합니다. 출금 계좌에서 2,000원이 빠지면 안 됩니다.
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

    // 1,500원 계좌에 서로 다른 1,000원 이체 둘이 경쟁합니다.
    // 하나만 완료되고 다른 하나는 잔액 부족으로 승인 상태에 남아야 합니다. 남은 잔액은 500원입니다.
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

    // A→B 250원과 B→A 500원을 함께 실행합니다. 처음에 각각 1,000원이면 마지막에는 1,250원과 750원입니다.
    // 두 작업이 계좌를 서로 반대 순서로 잠그면 서로 기다릴 수 있습니다. 같은 순서로 잠그는 경로를 시험합니다.
    // 이 실행이 제시간에 끝나는지 확인하는 시험이며, 모든 동시 실행 상황을 증명하는 것은 아닙니다.
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

    // 출금 회사에 속하지만 요청자와는 다른 사용자를 만듭니다. 자기 결재가 아닌 상황을 준비합니다.
    private UUID reviewer() {
        var company = companies.findById(accounts.findById(sourceId).orElseThrow().getCompany().getId()).orElseThrow();
        return users.saveAndFlush(new AppUser(company, "https://identity.example", "reviewer")).getId();
    }

    // 출금 회사가 아닌 다른 회사 사용자를 만듭니다. 이 사람이 처리하면 거절해야 하는 시험에 씁니다.
    private UUID outsider() {
        var company = companies.findById(accounts.findById(otherCompanyAccountId).orElseThrow().getCompany().getId()).orElseThrow();
        return users.saveAndFlush(new AppUser(company, "https://identity.example", "outsider")).getId();
    }

    // 여러 시험에서 쓸 같은 회사 계좌 간 1,000원 요청을 만들고 그 요청 ID를 돌려줍니다.
    private UUID newRequest() {
        return service.request(sourceId, destinationId, 1000L, requesterId).id();
    }

    // approve와 reject를 각각 실행해 상태·처리자·처리 시각이 응답과 DB에 남는지 확인합니다.
    // 결재만 했으므로 잔액은 0원 그대로이고, 이미 결재한 요청을 다시 결재하면 거절해야 합니다.
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
        for (String again : new String[]{"approve", "reject"}) {
            mvc.perform(post("/api/transfers/" + id + "/" + again).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"actorId\":\"" + actor + "\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        }
        assertThat(transfers.findById(id).orElseThrow().getReviewedAt()).isEqualTo(loaded.getReviewedAt());
    }

    // 요청자 자신과 다른 회사 사용자는 승인도 반려도 할 수 없어야 합니다. 원래 요청 상태를 유지해야 합니다.
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

    // 처리자 ID 누락은 400, 없는 처리자나 이체 요청은 404가 되어야 합니다.
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

    // POST 요청으로 1,000원 이체 요청서를 만들고 201(생성 성공)을 확인합니다.
    // DB에는 요청 한 건만 남고, 출금 잔액은 아직 변하지 않아야 합니다.
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

    // 0·음수·null·소수·long 범위 초과 값을 각각 보내 봅니다. 정수 원 단위의 유효한 금액만 허용해야 합니다.
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "null", "1.5", "9223372036854775808"})
    void rejectsInvalidAmounts(String amount) throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(body(sourceId, destinationId, amount, requesterId)))
                .andExpect(status().isBadRequest());
        assertThat(transfers.count()).isZero();
    }

    // 필수값 누락, 깨진 JSON, null 본문, 빈 본문, 잘못된 UUID는 400으로 거절하고 저장하지 않아야 합니다.
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "null", "", "{\"sourceAccountId\":\"not-a-uuid\"}"})
    void rejectsMissingOrMalformedBody(String json) throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        assertThat(transfers.count()).isZero();
    }

    // 출금과 입금 계좌 ID가 같은 요청은 400으로 거절해야 합니다.
    @Test
    void rejectsSameAccount() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(body(sourceId, sourceId, "1000", requesterId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertThat(transfers.count()).isZero();
    }

    // 출금 계좌·입금 계좌·요청자 중 하나만 없는 ID로 바꿔서 404 응답과 미저장을 확인합니다.
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

    // 다른 회사 계좌에서 출금하는 요청은 403(허용되지 않음)을 반환하고 저장하지 않아야 합니다.
    @Test
    void companyMismatchReturns403() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(body(otherCompanyAccountId, destinationId, "1000", requesterId)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        assertThat(transfers.count()).isZero();
    }

    // 시험에 보낼 JSON 글을 만드는 도우미입니다. %s 자리에 formatted의 값이 순서대로 들어갑니다.
    // 여기서는 정해진 시험 값과 UUID만 넣습니다. 실제 사용자 입력으로 JSON을 직접 조립하는 예시는 아닙니다.
    private String body(UUID source, UUID destination, String amount, UUID requester) {
        return """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amountWon":%s,"requesterId":"%s"}
                """.formatted(source, destination, amount, requester);
    }
}
