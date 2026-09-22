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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 이체 요청을 별도 PostgreSQL에 저장하고 재조회합니다. 개발용 DB와 분리됩니다.
// 테스트에 Transactional을 붙이지 않아 서비스가 시작한 트랜잭션의 실제 commit을 확인합니다.
// 제약 위반 테스트는 예외를 예상하는 테스트이며 실패 로그가 보여도 의도한 결과일 수 있습니다.
@SpringBootTest
@Testcontainers
class TransferRequestServiceTest {

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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void commitsRequestForSameOrOtherCompanyRecipient(boolean otherCompany) {
        UUID target = otherCompany ? otherCompanyAccountId : destinationId;
        var result = service.request(sourceId, target, 1000L, requesterId);
        // 서비스 호출이 끝난 뒤 별도 저장소 조회로 커밋된 결과를 확인합니다.
        TransferRequest loaded = transfers.findById(result.id()).orElseThrow();
        assertThat(loaded.getSourceAccountId()).isEqualTo(sourceId);
        assertThat(loaded.getDestinationAccountId()).isEqualTo(target);
        assertThat(loaded.getRequesterId()).isEqualTo(requesterId);
        assertThat(loaded.getAmountWon()).isEqualTo(1000L);
        assertThat(loaded.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(result).isEqualTo(new TransferRequestService.RequestedTransfer(
                loaded.getId(), sourceId, target, 1000L, requesterId, TransferStatus.REQUESTED));
        // 요청 생성은 출금이나 입금이 아닙니다. 잔액 0원이어도 요청은 생성됩니다.
        assertThat(accounts.findById(sourceId).orElseThrow().getBalanceWon()).isZero();
        assertThat(accounts.findById(target).orElseThrow().getBalanceWon()).isZero();
    }

    @Test
    void rejectsOtherCompanySourceWithoutSaving() {
        assertThatThrownBy(() -> service.request(otherCompanyAccountId, destinationId, 1000L, requesterId))
                .isInstanceOf(TransferCompanyMismatchException.class);
        assertThat(transfers.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "destination", "requester"})
    void rejectsMissingReferencesWithoutSaving(String missing) {
        UUID unknown = UUID.randomUUID();
        UUID source = missing.equals("source") ? unknown : sourceId;
        UUID destination = missing.equals("destination") ? unknown : destinationId;
        UUID requester = missing.equals("requester") ? unknown : requesterId;
        assertThatThrownBy(() -> service.request(source, destination, 1000L, requester))
                .isInstanceOf(TransferReferenceNotFoundException.class)
                .hasMessageContaining(unknown.toString());
        assertThat(transfers.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nullSource", "nullDestination", "nullRequester", "sameAccount", "zero", "negative"})
    void rejectsInvalidInputWithoutSaving(String invalid) {
        // 삼항 연산자 조건 ? 참일 때 값 : 거짓일 때 값으로 한 입력만 잘못되게 바꿉니다.
        UUID source = invalid.equals("nullSource") ? null : sourceId;
        UUID destination = invalid.equals("nullDestination") ? null
                : invalid.equals("sameAccount") ? sourceId : destinationId;
        UUID requester = invalid.equals("nullRequester") ? null : requesterId;
        long amount = invalid.equals("zero") ? 0L : invalid.equals("negative") ? -1L : 1000L;
        assertThatThrownBy(() -> service.request(source, destination, amount, requester))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(transfers.count()).isZero();
    }
}
