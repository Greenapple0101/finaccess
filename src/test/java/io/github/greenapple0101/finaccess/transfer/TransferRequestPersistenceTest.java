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

// 이체 요청을 별도 PostgreSQL에 저장하고 재조회합니다. 개발용 DB와 분리됩니다.
// 클래스의 Transactional은 각 테스트를 트랜잭션으로 실행하고 종료 후 기본적으로 롤백합니다.
// 제약 위반 테스트는 예외를 예상하는 테스트이며 실패 로그가 보여도 의도한 결과일 수 있습니다.
@SpringBootTest
@Testcontainers
@Transactional
class TransferRequestPersistenceTest {

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
    @Autowired EntityManager entityManager;
    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired JdbcTemplate jdbc;

    @Autowired TransferRequestRepository transfers;
    @Autowired AppUserRepository users;
    private UUID sourceId;
    private UUID destinationId;
    private UUID requesterId;

    // @BeforeEach는 각 테스트 실행 전에 필요한 회사·계좌·사용자를 준비합니다.
    // 각 테스트는 독립된 트랜잭션에서 실행되고 종료 후 롤백됩니다.
    @BeforeEach
    void prepareReferences() {
        Company company = companies.saveAndFlush(new Company("이체 테스트 회사"));
        sourceId = accounts.saveAndFlush(new Account(company)).getId();
        destinationId = accounts.saveAndFlush(new Account(company)).getId();
        requesterId = users.saveAndFlush(new AppUser(company, "https://identity.example", "requester")).getId();
    }

    @Test
    void savesAndReloadsRequestWithoutMovingMoney() {
        TransferRequest saved = transfers.saveAndFlush(
                new TransferRequest(sourceId, destinationId, 1000L, requesterId));
        UUID id = saved.getId();
        assertThat(id).isNotNull();
        // clear는 메모리의 관리 객체를 분리합니다. 행 삭제가 아닙니다.
        // 이후 findById가 기존 객체 재사용 대신 DB를 읽게 하여 매핑을 검증합니다.
        entityManager.clear();
        TransferRequest loaded = transfers.findById(id).orElseThrow();
        assertThat(loaded.getSourceAccountId()).isEqualTo(sourceId);
        assertThat(loaded.getDestinationAccountId()).isEqualTo(destinationId);
        assertThat(loaded.getRequesterId()).isEqualTo(requesterId);
        assertThat(loaded.getAmountWon()).isEqualTo(1000L);
        assertThat(loaded.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(loaded.getCreatedAt()).isNotNull();
        // enum이 DB에서 문자열로 보관되는지도 SQL로 확인합니다.
        assertThat(jdbc.queryForObject("SELECT status FROM transfer_requests WHERE id = ?", String.class, id))
                .isEqualTo("REQUESTED");
        // 요청의 저장만 수행했으므로 잔액은 여전히 0원입니다.
        assertThat(accounts.findById(sourceId).orElseThrow().getBalanceWon()).isZero();
        assertThat(accounts.findById(destinationId).orElseThrow().getBalanceWon()).isZero();
    }

    // Java 생성자를 우회한 SQL에도 DB 제약이 적용되는지 확인합니다.
    // ValueSource의 각 값마다 테스트가 별도로 실행됩니다.
    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void databaseRejectsNonpositiveAmount(long amount) {
        assertThatThrownBy(() -> insert(sourceId, destinationId, requesterId, amount, "REQUESTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsSameAccount() {
        assertThatThrownBy(() -> insert(sourceId, sourceId, requesterId, 1000L, "REQUESTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownStatus() {
        assertThatThrownBy(() -> insert(sourceId, destinationId, requesterId, 1000L, "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "destination", "requester"})
    void databaseRejectsMissingReferences(String missing) {
        // 한 번에 한 외래키만 존재하지 않는 ID로 바꿔 세 관계를 각각 검사합니다.
        UUID source = missing.equals("source") ? UUID.randomUUID() : sourceId;
        UUID destination = missing.equals("destination") ? UUID.randomUUID() : destinationId;
        UUID requester = missing.equals("requester") ? UUID.randomUUID() : requesterId;
        assertThatThrownBy(() -> insert(source, destination, requester, 1000L, "REQUESTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseDefaultsStatusAndTimestamp() {
        UUID id = UUID.randomUUID();
        // status와 created_at을 생략하여 SQL DEFAULT가 적용되는지 검사합니다.
        jdbc.update("""
                INSERT INTO transfer_requests (id, source_account_id, destination_account_id, amount_won, requester_id)
                VALUES (?, ?, ?, ?, ?)
                """, id, sourceId, destinationId, 1000L, requesterId);
        TransferRequest loaded = transfers.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    // 반복되는 SQL을 모은 테스트 전용 보조 메서드입니다. 운영 API가 아닙니다.
    // ? 위치에는 뒤에 전달한 값들이 바인딩됩니다. 문자열 연결로 SQL을 조립하지 않습니다.
    private void insert(UUID source, UUID destination, UUID requester, long amount, String status) {
        jdbc.update("""
                INSERT INTO transfer_requests (id, source_account_id, destination_account_id, requester_id, amount_won, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), source, destination, requester, amount, status);
    }
}
