// [이 파일의 역할] 이체 요청서를 PostgreSQL에 보관하고 다시 읽을 수 있는지 확인합니다.
// 회사·계좌·사용자 준비 → 요청서 저장 → 다시 조회 → 값 확인 순서로 읽으세요.
// 요청서를 저장하는 것은 '이체해 주세요'라고 접수하는 일입니다. 아직 잔액은 바뀌지 않습니다.
// Java의 검사 코드를 거치지 않고 SQL로 잘못된 값을 넣어도 DB가 거절하는지 시험합니다.
// Spring과 실제 DB를 함께 켜는 통합 테스트이며, Testcontainers를 위해 Docker가 필요합니다.
// @Transactional 때문에 각 시험의 DB 변경은 끝나면 자동 취소됩니다(롤백).
// assertThatThrownBy는 지정한 오류가 나야 통과한다는 뜻입니다. 거절을 확인하는 시험에 사용합니다.

package io.github.greenapple0101.finaccess.transfer;

import io.github.greenapple0101.finaccess.account.Account;
import io.github.greenapple0101.finaccess.account.AccountRepository;
import io.github.greenapple0101.finaccess.user.AppUser;
import io.github.greenapple0101.finaccess.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
class TransferRequestPersistenceTest {

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
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @Autowired TransferRequestRepository transfers;
    @Autowired AppUserRepository users;
    private UUID sourceId;
    private UUID destinationId;
    private UUID requesterId;

    // 각 시험 전에 회사·계좌·요청자를 새로 준비합니다. 앞 시험의 데이터에 기대지 않도록 합니다.
    @BeforeEach
    void prepareReferences() {
        Company company = companies.saveAndFlush(new Company("이체 테스트 회사"));
        sourceId = accounts.saveAndFlush(new Account(company)).getId();
        destinationId = accounts.saveAndFlush(new Account(company)).getId();
        requesterId = users.saveAndFlush(new AppUser(company, "https://identity.example", "requester")).getId();
    }

    // 1,000원 요청서를 저장하고, 계좌·요청자·금액·상태가 DB에도 그대로 남았는지 확인합니다.
    @Test
    void savesAndReloadsRequestWithoutMovingMoney() {
        TransferRequest saved = transfers.saveAndFlush(
                new TransferRequest(sourceId, destinationId, 1000L, requesterId));
        UUID id = saved.getId();
        assertThat(id).isNotNull();
        // clear는 JPA가 기억하는 객체를 분리합니다. DB의 행을 삭제하는 명령이 아닙니다.
        // 이후 조회가 방금 만든 메모리 객체를 그대로 돌려주는 데 그치지 않게 합니다.
        entityManager.clear();
        TransferRequest loaded = transfers.findById(id).orElseThrow();
        assertThat(loaded.getSourceAccountId()).isEqualTo(sourceId);
        assertThat(loaded.getDestinationAccountId()).isEqualTo(destinationId);
        assertThat(loaded.getRequesterId()).isEqualTo(requesterId);
        assertThat(loaded.getAmountWon()).isEqualTo(1000L);
        assertThat(loaded.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(loaded.getCreatedAt()).isNotNull();
        // DB에도 상태를 숫자 순번이 아닌 REQUESTED라는 이름으로 저장했는지 SQL로 직접 확인합니다.
        assertThat(jdbc.queryForObject("SELECT status FROM transfer_requests WHERE id = ?", String.class, id))
                .isEqualTo("REQUESTED");
        assertThat(accounts.findById(sourceId).orElseThrow().getBalanceWon()).isZero();
        assertThat(accounts.findById(destinationId).orElseThrow().getBalanceWon()).isZero();
    }

    // @ValueSource의 0과 -1을 하나씩 amount에 넣어, DB가 양수가 아닌 금액을 거절하는지 확인합니다.
    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void databaseRejectsNonpositiveAmount(long amount) {
        assertThatThrownBy(() -> insert(sourceId, destinationId, requesterId, amount, "REQUESTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // 보내는 계좌와 받는 계좌가 같은 요청서는 DB에서도 거절해야 합니다.
    @Test
    void databaseRejectsSameAccount() {
        assertThatThrownBy(() -> insert(sourceId, sourceId, requesterId, 1000L, "REQUESTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // 정해 놓은 네 가지 상태에 없는 UNKNOWN은 저장되면 안 됩니다.
    @Test
    void databaseRejectsUnknownStatus() {
        assertThatThrownBy(() -> insert(sourceId, destinationId, requesterId, 1000L, "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // 계좌·사용자 중 하나만 존재하지 않는 ID로 바꾸고, DB가 거절하는지 각각 확인합니다.
    @ParameterizedTest
    @ValueSource(strings = {"source", "destination", "requester"})
    void databaseRejectsMissingReferences(String missing) {
        UUID source = missing.equals("source") ? UUID.randomUUID() : sourceId;
        UUID destination = missing.equals("destination") ? UUID.randomUUID() : destinationId;
        UUID requester = missing.equals("requester") ? UUID.randomUUID() : requesterId;
        assertThatThrownBy(() -> insert(source, destination, requester, 1000L, "REQUESTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // 상태와 생성 시각을 생략해도 DB가 REQUESTED와 생성 시각을 채우는지 확인합니다.
    @Test
    void databaseDefaultsStatusAndTimestamp() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO transfer_requests (id, source_account_id, destination_account_id, amount_won, requester_id)
                VALUES (?, ?, ?, ?, ?)
                """, id, sourceId, destinationId, 1000L, requesterId);
        TransferRequest loaded = transfers.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    // 이 파일의 여러 시험에서 같은 SQL을 쓰므로 한곳에 모은 도우미입니다.
    // SQL의 ?는 값을 넣을 자리입니다. 뒤에 나열한 값이 순서대로 들어가며, SQL 글자를 이어 붙이지 않습니다.
    private void insert(UUID source, UUID destination, UUID requester, long amount, String status) {
        jdbc.update("""
                INSERT INTO transfer_requests (id, source_account_id, destination_account_id, requester_id, amount_won, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), source, destination, requester, amount, status);
    }
}
