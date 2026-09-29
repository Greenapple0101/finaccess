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
import io.github.greenapple0101.finaccess.account.InsufficientBalanceException;
import io.github.greenapple0101.finaccess.company.Company;
import io.github.greenapple0101.finaccess.company.CompanyRepository;
import io.github.greenapple0101.finaccess.user.AppUser;
import io.github.greenapple0101.finaccess.user.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @SpringBootTest는 실제 서비스와 저장소 등 앱의 Spring 구성을 준비합니다.
@SpringBootTest
@AutoConfigureMockMvc
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
    @Autowired AccountRepository accountRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransferRequestRepository transferRequestRepository;
    @Autowired AppUserRepository appUserRepository;
    @Autowired TransferRequestService transferRequestService;
    @Autowired TransferWorkflowService transferWorkflowService;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private UUID requesterId;
    private UUID otherCompanyAccountId;

    // @BeforeEach 때문에 시험마다 회사·계좌·요청자를 새로 준비합니다.
    // source는 출금, destination은 입금 계좌입니다. 다른 회사 계좌도 만들어 허용 범위를 검사합니다.
    @BeforeEach
    void prepareReferences() {
        Company requesterCompany = companyRepository.saveAndFlush(new Company("요청 회사"));
        Company otherCompany = companyRepository.saveAndFlush(new Company("수취 회사"));
        sourceAccountId = accountRepository.saveAndFlush(new Account(requesterCompany)).getId();
        destinationAccountId = accountRepository.saveAndFlush(new Account(requesterCompany)).getId();
        otherCompanyAccountId = accountRepository.saveAndFlush(new Account(otherCompany)).getId();
        AppUser requester = new AppUser(requesterCompany, "https://identity.example", "requester");
        requesterId = appUserRepository.saveAndFlush(requester).getId();
    }

    // @AfterEach는 각 시험이 끝난 뒤 정리할 일을 표시합니다. 여기서는 커밋된 시험 데이터를 지웁니다.
    // 요청서가 계좌를 참조하므로 요청서부터 지웁니다. 이런 연결 규칙을 외래키라고 합니다.
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM transfer_requests");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update("DELETE FROM app_users");
        jdbcTemplate.update("DELETE FROM companies");
    }

    // 시험 시작 잔액을 SQL로 맞추는 도우미입니다. 실제 충전 기능이나 입금 API가 아닙니다.
    private void setBalance(UUID accountId, long balanceWon) {
        jdbcTemplate.update("UPDATE accounts SET balance_won = ? WHERE id = ?", balanceWon, accountId);
    }

    // DB에서 두 계좌를 다시 읽어 예상 잔액과 비교합니다. 응답에 성공이라고만 적혔는지 보는 데 그치지 않습니다.
    private void assertBalances(long expectedSourceBalance, long expectedDestinationBalance) {
        Account sourceAccount = accountRepository.findById(sourceAccountId).orElseThrow();
        Account destinationAccount = accountRepository.findById(destinationAccountId).orElseThrow();
        assertThat(sourceAccount.getBalanceWon()).isEqualTo(expectedSourceBalance);
        assertThat(destinationAccount.getBalanceWon()).isEqualTo(expectedDestinationBalance);
    }

    // 실행이 실패한 요청은 승인 상태로 남고, 결재 기록은 유지되며, 실행자·완료 시각은 없어야 합니다.
    private void assertApprovedWithoutCompletion(UUID transferId) {
        TransferRequest savedTransfer = transferRequestRepository.findById(transferId).orElseThrow();
        assertThat(savedTransfer.getStatus()).isEqualTo(TransferStatus.APPROVED);
        assertThat(savedTransfer.getReviewerId()).isNotNull();
        assertThat(savedTransfer.getReviewedAt()).isNotNull();
        assertThat(savedTransfer.getExecutedBy()).isNull();
        assertThat(savedTransfer.getCompletedAt()).isNull();
    }

    // 출금 1,500원·입금 200원 준비 → 1,000원 요청 → 승인 → 실행 → 500원·1,200원 확인 순서입니다.
    // 같은 이체를 다시 실행하면 409(현재 상태와 충돌)로 거절하고, 돈이 두 번 나가지 않아야 합니다.
    @Test
    void fullHttpFlowMovesMoneyOnce() throws Exception {
        setBalance(sourceAccountId, 1500L);
        setBalance(destinationAccountId, 200L);
        String responseJson = mockMvc.perform(post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(sourceAccountId, destinationAccountId, "1000", requesterId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // 응답 JSON 글을 Java 결과 객체로 바꾸고, 방금 생성된 요청 ID를 꺼내 다음 URL에 씁니다.
        TransferRequestService.RequestedTransfer response =
                objectMapper.readValue(responseJson, TransferRequestService.RequestedTransfer.class);
        UUID transferId = response.id();
        UUID actorId = createReviewer();
        mockMvc.perform(post("/api/transfers/" + transferId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(actorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(post("/api/transfers/" + transferId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(actorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.executedBy").value(actorId.toString()))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        assertBalances(500L, 1200L);
        TransferRequest savedTransfer = transferRequestRepository.findById(transferId).orElseThrow();
        assertThat(savedTransfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(savedTransfer.getExecutedBy()).isEqualTo(actorId);
        assertThat(savedTransfer.getCompletedAt()).isNotNull();
        mockMvc.perform(post("/api/transfers/" + transferId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(actorId)))
                .andExpect(status().isConflict());
        assertBalances(500L, 1200L);
    }

    // false일 때는 요청됨, true일 때는 반려됨 상태로 시험합니다. 둘 다 승인된 요청이 아니므로 실행 불가입니다.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refusesRequestedOrRejectedExecution(boolean reject) throws Exception {
        UUID transferId = createRequest();
        UUID actorId = createReviewer();
        if (reject) {
            transferWorkflowService.reject(transferId, actorId);
        }
        mockMvc.perform(post("/api/transfers/" + transferId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(actorId)))
                .andExpect(status().isConflict());
        assertBalances(0L, 0L);
        assertThat(transferRequestRepository.findById(transferId).orElseThrow().getCompletedAt()).isNull();
    }

    // 1,000원을 보내려는데 999원만 있으면 실패해야 합니다. 돈과 승인 기록이 그대로인지 확인합니다.
    @Test
    void insufficientFundsLeaveApprovalAndBalancesUnchanged() throws Exception {
        UUID transferId = createRequest();
        UUID actorId = createReviewer();
        transferWorkflowService.approve(transferId, actorId);
        setBalance(sourceAccountId, 999L);
        mockMvc.perform(post("/api/transfers/" + transferId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(actorId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertBalances(999L, 0L);
        assertApprovedWithoutCompletion(transferId);
    }

    // 입금 계좌 잔액을 long의 최대값으로 만들어 1원도 더 못 받게 합니다.
    // 서비스는 출금 SQL을 이미 DB에 보낸 뒤 입금 계산에 실패합니다.
    // DB로 보낸 것(flush)과 저장 확정(commit)은 다릅니다. 아직 확정 전이므로 출금도 취소되어야 합니다.
    @Test
    void destinationOverflowRollsBackAlreadyFlushedWithdrawal() throws Exception {
        UUID transferId = createRequest();
        UUID actorId = createReviewer();
        transferWorkflowService.approve(transferId, actorId);
        setBalance(sourceAccountId, 1000L);
        setBalance(destinationAccountId, Long.MAX_VALUE);
        mockMvc.perform(post("/api/transfers/" + transferId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(actorId)))
                .andExpect(status().isConflict());
        assertBalances(1000L, Long.MAX_VALUE);
        assertApprovedWithoutCompletion(transferId);
    }

    // 계좌 계산이 끝났어도, 마지막 완료 기록을 저장하지 못하면 전부 취소되어야 합니다.
    // 이 시험은 테스트 DB에만 "COMPLETED 저장 금지" 규칙을 잠깐 넣어 마지막 저장을 일부러 실패시킵니다.
    @Test
    void completionWriteFailureRollsBackBothAccounts() {
        UUID transferId = createRequest();
        UUID actorId = createReviewer();
        transferWorkflowService.approve(transferId, actorId);
        setBalance(sourceAccountId, 1500L);
        setBalance(destinationAccountId, 200L);
        // commit 과정에서 완료 상태 저장이 거절되도록 임시 DB 규칙을 추가합니다.
        jdbcTemplate.execute("ALTER TABLE transfer_requests ADD CONSTRAINT ck_test_no_completion CHECK (status <> 'COMPLETED')");
        try {
            assertThatThrownBy(() -> transferWorkflowService.execute(transferId, actorId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertBalances(1500L, 200L);
            assertApprovedWithoutCompletion(transferId);
        } finally {
            // finally는 시험 확인이 실패해도 실행됩니다. 임시 규칙을 제거해 다른 시험에 영향을 주지 않습니다.
            jdbcTemplate.execute("ALTER TABLE transfer_requests DROP CONSTRAINT ck_test_no_completion");
        }
    }

    // 실행자 누락은 400, 다른 회사 사용자는 403, 없는 사용자나 요청은 404인지 확인합니다.
    // 어느 경우든 잔액과 승인 상태가 바뀌면 안 됩니다.
    @Test
    void executionValidatesActorAndReference() throws Exception {
        UUID transferId = createRequest();
        transferWorkflowService.approve(transferId, createReviewer());
        setBalance(sourceAccountId, 1000L);
        mockMvc.perform(post("/api/transfers/" + transferId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/transfers/" + transferId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(createOtherCompanyUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/transfers/" + transferId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/transfers/" + UUID.randomUUID() + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(requesterId)))
                .andExpect(status().isNotFound());
        assertBalances(1000L, 0L);
        assertApprovedWithoutCompletion(transferId);
    }

    // 다른 회사로 잔액 전부인 1,000원을 보내면 출금 계좌는 0원, 수취 계좌는 1,000원이 되어야 합니다.
    @Test
    void executesToOtherCompanyAndAllowsEntireBalance() {
        UUID actorId = createReviewer();
        UUID transferId = transferRequestService.request(sourceAccountId, otherCompanyAccountId, 1000L, requesterId).id();
        transferWorkflowService.approve(transferId, actorId);
        setBalance(sourceAccountId, 1000L);
        transferWorkflowService.execute(transferId, actorId);
        assertThat(accountRepository.findById(sourceAccountId).orElseThrow().getBalanceWon()).isZero();
        assertThat(accountRepository.findById(otherCompanyAccountId).orElseThrow().getBalanceWon()).isEqualTo(1000L);
    }

    // 동작 두 개를 거의 동시에 시작해 서로 영향을 주는 상황을 만드는 시험 도우미입니다.
    // 스레드는 따로 실행되는 작업 흐름입니다. 두 스레드가 서비스를 각각 호출해 별도 DB 트랜잭션을 시작합니다.
    // ready는 둘 다 준비됐다는 신호, start는 함께 출발하라는 신호입니다.
    // 출발을 맞춰도 DB의 같은 줄에 정확히 동시에 도착한다고 보장되지는 않습니다.
    private List<String> runConcurrently(Runnable firstAction, Runnable secondAction) throws Exception {
        // 동시에 일할 작업자 두 명을 준비합니다. 작업자는 별도 스레드에서 코드를 실행합니다.
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> pendingResults = new ArrayList<>();
            for (Runnable action : new Runnable[]{firstAction, secondAction}) {
                // submit은 작업자에게 동작을 맡깁니다. Future는 나중에 완료 결과를 받을 때 쓰는 보관함입니다.
                Future<String> pendingResult = workers.submit(() -> {
                    // 준비된 작업자 수를 표시한 뒤, 공동 출발 신호를 기다립니다. 10초가 지나도 신호가 없으면 실패합니다.
                    ready.countDown();
                    boolean startSignalArrived = start.await(10, TimeUnit.SECONDS);
                    if (!startSignalArrived) {
                        throw new AssertionError("Start timed out");
                    }
                    try {
                        action.run();
                        return "success";
                    } catch (TransferStateConflictException | InsufficientBalanceException error) {
                        return error.getClass().getSimpleName();
                    }
                });
                pendingResults.add(pendingResult);
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            // 두 작업자가 준비됐으면 출발 신호를 보냅니다. 어떤 작업이 먼저 끝날지는 정하지 않습니다.
            start.countDown();
            List<String> results = new ArrayList<>();
            // 각 결과를 최대 20초 기다립니다. 잠금 문제로 끝없이 기다리는 시험을 방지합니다.
            for (Future<String> pendingResult : pendingResults) {
                results.add(pendingResult.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            // 오류가 나도 작업자들을 종료하고, 종료까지 끝났는지 확인합니다.
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    // 같은 요청을 승인하는 작업과 반려하는 작업이 경쟁하면 하나만 성공해야 합니다.
    // 먼저 처리된 상태가 남고, 나중 작업은 이미 결재됐다는 오류를 받아야 합니다.
    @Test
    void simultaneousReviewHasOnlyOneWinner() throws Exception {
        UUID transferId = createRequest();
        UUID actorId = createReviewer();
        assertThat(runConcurrently(
                () -> transferWorkflowService.approve(transferId, actorId),
                () -> transferWorkflowService.reject(transferId, actorId)))
                .containsExactlyInAnyOrder("success", "TransferStateConflictException");
        TransferRequest savedTransfer = transferRequestRepository.findById(transferId).orElseThrow();
        assertThat(savedTransfer.getStatus()).isIn(TransferStatus.APPROVED, TransferStatus.REJECTED);
        assertThat(savedTransfer.getReviewerId()).isEqualTo(actorId);
    }

    // 동시에 같은 1,000원 이체를 실행해도 한 번만 성공해야 합니다. 출금 계좌에서 2,000원이 빠지면 안 됩니다.
    @Test
    void simultaneousSameRequestExecutesOnce() throws Exception {
        UUID transferId = createRequest();
        UUID actorId = createReviewer();
        transferWorkflowService.approve(transferId, actorId);
        setBalance(sourceAccountId, 2000L);
        assertThat(runConcurrently(
                () -> transferWorkflowService.execute(transferId, actorId),
                () -> transferWorkflowService.execute(transferId, actorId)))
                .containsExactlyInAnyOrder("success", "TransferStateConflictException");
        assertBalances(1000L, 1000L);
        assertThat(transferRequestRepository.findById(transferId).orElseThrow().getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }

    // 1,500원 계좌에 서로 다른 1,000원 이체 둘이 경쟁합니다.
    // 하나만 완료되고 다른 하나는 잔액 부족으로 승인 상태에 남아야 합니다. 남은 잔액은 500원입니다.
    @Test
    void competingRequestsCannotOverdrawSharedAccount() throws Exception {
        UUID firstTransferId = createRequest();
        UUID secondTransferId = createRequest();
        UUID actorId = createReviewer();
        transferWorkflowService.approve(firstTransferId, actorId);
        transferWorkflowService.approve(secondTransferId, actorId);
        setBalance(sourceAccountId, 1500L);
        assertThat(runConcurrently(
                () -> transferWorkflowService.execute(firstTransferId, actorId),
                () -> transferWorkflowService.execute(secondTransferId, actorId)))
                .containsExactlyInAnyOrder("success", "InsufficientBalanceException");
        assertBalances(500L, 1000L);
        List<TransferStatus> savedStatuses = new ArrayList<>();
        for (TransferRequest savedTransfer : transferRequestRepository.findAll()) {
            savedStatuses.add(savedTransfer.getStatus());
        }
        assertThat(savedStatuses).containsExactlyInAnyOrder(TransferStatus.COMPLETED, TransferStatus.APPROVED);
    }

    // A→B 250원과 B→A 500원을 함께 실행합니다. 처음에 각각 1,000원이면 마지막에는 1,250원과 750원입니다.
    // 두 작업이 계좌를 서로 반대 순서로 잠그면 서로 기다릴 수 있습니다. 같은 순서로 잠그는 경로를 시험합니다.
    // 이 실행이 제시간에 끝나는지 확인하는 시험이며, 모든 동시 실행 상황을 증명하는 것은 아닙니다.
    @Test
    void oppositeDirectionTransfersUseConsistentLockOrder() throws Exception {
        UUID firstTransferId = transferRequestService.request(sourceAccountId, destinationAccountId, 250L, requesterId).id();
        UUID secondTransferId = transferRequestService.request(destinationAccountId, sourceAccountId, 500L, requesterId).id();
        UUID actorId = createReviewer();
        transferWorkflowService.approve(firstTransferId, actorId);
        transferWorkflowService.approve(secondTransferId, actorId);
        setBalance(sourceAccountId, 1000L);
        setBalance(destinationAccountId, 1000L);
        assertThat(runConcurrently(
                () -> transferWorkflowService.execute(firstTransferId, actorId),
                () -> transferWorkflowService.execute(secondTransferId, actorId)))
                .containsExactly("success", "success");
        assertBalances(1250L, 750L);
    }

    // 출금 회사에 속하지만 요청자와는 다른 사용자를 만듭니다. 자기 결재가 아닌 상황을 준비합니다.
    private UUID createReviewer() {
        Account sourceAccount = accountRepository.findById(sourceAccountId).orElseThrow();
        UUID companyId = sourceAccount.getCompany().getId();
        Company company = companyRepository.findById(companyId).orElseThrow();
        AppUser reviewer = new AppUser(company, "https://identity.example", "reviewer");
        return appUserRepository.saveAndFlush(reviewer).getId();
    }

    // 출금 회사가 아닌 다른 회사 사용자를 만듭니다. 이 사람이 처리하면 거절해야 하는 시험에 씁니다.
    private UUID createOtherCompanyUser() {
        Account otherCompanyAccount = accountRepository.findById(otherCompanyAccountId).orElseThrow();
        UUID companyId = otherCompanyAccount.getCompany().getId();
        Company company = companyRepository.findById(companyId).orElseThrow();
        AppUser otherCompanyUser = new AppUser(company, "https://identity.example", "outsider");
        return appUserRepository.saveAndFlush(otherCompanyUser).getId();
    }

    // 여러 시험에서 쓸 같은 회사 계좌 간 1,000원 요청을 만들고 그 요청 ID를 돌려줍니다.
    private UUID createRequest() {
        return transferRequestService.request(sourceAccountId, destinationAccountId, 1000L, requesterId).id();
    }

    // approve와 reject를 각각 실행해 상태·처리자·처리 시각이 응답과 DB에 남는지 확인합니다.
    // 결재만 했으므로 잔액은 0원 그대로이고, 이미 결재한 요청을 다시 결재하면 거절해야 합니다.
    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void reviewsAndPersistsActorAndTime(String action) throws Exception {
        UUID transferId = createRequest();
        UUID actorId = createReviewer();
        String expectedStatus;
        if (action.equals("approve")) {
            expectedStatus = "APPROVED";
        } else {
            expectedStatus = "REJECTED";
        }
        mockMvc.perform(post("/api/transfers/" + transferId + "/" + action)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(actorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.reviewerId").value(actorId.toString()))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
        TransferRequest savedTransfer = transferRequestRepository.findById(transferId).orElseThrow();
        assertThat(savedTransfer.getStatus().name()).isEqualTo(expectedStatus);
        assertThat(savedTransfer.getReviewerId()).isEqualTo(actorId);
        assertThat(savedTransfer.getReviewedAt()).isNotNull();
        assertThat(accountRepository.findById(sourceAccountId).orElseThrow().getBalanceWon()).isZero();
        for (String repeatedAction : new String[]{"approve", "reject"}) {
            mockMvc.perform(post("/api/transfers/" + transferId + "/" + repeatedAction)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(actorBody(actorId)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }
        TransferRequest transferAfterRepeatedReview = transferRequestRepository.findById(transferId).orElseThrow();
        assertThat(transferAfterRepeatedReview.getReviewedAt()).isEqualTo(savedTransfer.getReviewedAt());
    }

    // 요청자 자신과 다른 회사 사용자는 승인도 반려도 할 수 없어야 합니다. 원래 요청 상태를 유지해야 합니다.
    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void rejectsSelfAndOtherCompanyReview(String action) throws Exception {
        UUID transferId = createRequest();
        for (UUID actorId : new UUID[]{requesterId, createOtherCompanyUser()}) {
            mockMvc.perform(post("/api/transfers/" + transferId + "/" + action)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(actorBody(actorId)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
        TransferRequest savedTransfer = transferRequestRepository.findById(transferId).orElseThrow();
        assertThat(savedTransfer.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(savedTransfer.getReviewerId()).isNull();
        assertThat(savedTransfer.getReviewedAt()).isNull();
    }

    // 처리자 ID 누락은 400, 없는 처리자나 이체 요청은 404가 되어야 합니다.
    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void rejectsMissingReviewActorAndTransfer(String action) throws Exception {
        UUID transferId = createRequest();
        mockMvc.perform(post("/api/transfers/" + transferId + "/" + action)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/transfers/" + transferId + "/" + action)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/transfers/" + UUID.randomUUID() + "/" + action)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actorBody(createReviewer())))
                .andExpect(status().isNotFound());
        assertThat(transferRequestRepository.findById(transferId).orElseThrow().getStatus()).isEqualTo(TransferStatus.REQUESTED);
    }

    // POST 요청으로 1,000원 이체 요청서를 만들고 201(생성 성공)을 확인합니다.
    // DB에는 요청 한 건만 남고, 출금 잔액은 아직 변하지 않아야 합니다.
    @Test
    void createsRequestAndCommits() throws Exception {
        mockMvc.perform(post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(sourceAccountId, destinationAccountId, "1000", requesterId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.amountWon").value(1000))
                .andExpect(jsonPath("$.sourceAccountId").value(sourceAccountId.toString()))
                .andExpect(jsonPath("$.destinationAccountId").value(destinationAccountId.toString()))
                .andExpect(jsonPath("$.requesterId").value(requesterId.toString()));
        assertThat(transferRequestRepository.count()).isEqualTo(1);
        assertThat(accountRepository.findById(sourceAccountId).orElseThrow().getBalanceWon()).isZero();
    }

    // 0·음수·null·소수·long 범위 초과 값을 각각 보내 봅니다. 정수 원 단위의 유효한 금액만 허용해야 합니다.
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "null", "1.5", "9223372036854775808"})
    void rejectsInvalidAmounts(String amount) throws Exception {
        mockMvc.perform(post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(sourceAccountId, destinationAccountId, amount, requesterId)))
                .andExpect(status().isBadRequest());
        assertThat(transferRequestRepository.count()).isZero();
    }

    // 필수값 누락, 깨진 JSON, null 본문, 빈 본문, 잘못된 UUID는 400으로 거절하고 저장하지 않아야 합니다.
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "null", "", "{\"sourceAccountId\":\"not-a-uuid\"}"})
    void rejectsMissingOrMalformedBody(String json) throws Exception {
        mockMvc.perform(post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
        assertThat(transferRequestRepository.count()).isZero();
    }

    // 출금과 입금 계좌 ID가 같은 요청은 400으로 거절해야 합니다.
    @Test
    void rejectsSameAccount() throws Exception {
        mockMvc.perform(post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(sourceAccountId, sourceAccountId, "1000", requesterId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertThat(transferRequestRepository.count()).isZero();
    }

    // 출금 계좌·입금 계좌·요청자 중 하나만 없는 ID로 바꿔서 404 응답과 미저장을 확인합니다.
    @ParameterizedTest
    @ValueSource(strings = {"source", "destination", "requester"})
    void missingReferenceReturns404(String missing) throws Exception {
        // 정상 ID를 먼저 준비한 뒤, 이번 시험에서 없는 것으로 정한 ID 하나만 바꿉니다.
        UUID requestedSourceId = sourceAccountId;
        UUID requestedDestinationId = destinationAccountId;
        UUID requestedUserId = requesterId;
        if (missing.equals("source")) {
            requestedSourceId = UUID.randomUUID();
        } else if (missing.equals("destination")) {
            requestedDestinationId = UUID.randomUUID();
        } else if (missing.equals("requester")) {
            requestedUserId = UUID.randomUUID();
        }

        mockMvc.perform(post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(requestedSourceId, requestedDestinationId, "1000", requestedUserId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        assertThat(transferRequestRepository.count()).isZero();
    }

    // 다른 회사 계좌에서 출금하는 요청은 403(허용되지 않음)을 반환하고 저장하지 않아야 합니다.
    @Test
    void companyMismatchReturns403() throws Exception {
        mockMvc.perform(post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(otherCompanyAccountId, destinationAccountId, "1000", requesterId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        assertThat(transferRequestRepository.count()).isZero();
    }

    // 시험에 보낼 JSON 글을 만드는 도우미입니다. %s 자리에 formatted의 값이 순서대로 들어갑니다.
    // 여기서는 정해진 시험 값과 UUID만 넣습니다. 실제 사용자 입력으로 JSON을 직접 조립하는 예시는 아닙니다.
    private String requestBody(UUID sourceAccountId, UUID destinationAccountId, String amountJson, UUID requesterId) {
        // amountJson은 일부러 글자로 받습니다. "null"이나 "1.5" 같은 잘못된 금액도 JSON에 넣어 시험합니다.
        return """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amountWon": %s,
                  "requesterId": "%s"
                }
                """.formatted(sourceAccountId, destinationAccountId, amountJson, requesterId);
    }

    // 승인·반려·실행 API에 보낼 처리자 ID입니다. 같은 JSON 조립 코드를 시험마다 반복하지 않도록 모았습니다.
    private String actorBody(UUID actorId) {
        return """
                {"actorId": "%s"}
                """.formatted(actorId);
    }
}
