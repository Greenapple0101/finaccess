// [이 파일의 역할] 이체 요청 서비스가 계좌·사용자·회사 소속을 확인한 뒤 저장하는지 검사합니다.
// 공통 데이터 준비 → transferRequestService.request(...) 호출 → DB에 남은 결과 확인 순서로 읽으세요.
// HTTP 요청은 보내지 않고 서비스를 직접 호출합니다. Spring과 실제 PostgreSQL은 사용합니다.
// 테스트 자체에는 @Transactional이 없습니다. 서비스가 저장을 확정한 결과를 확인하기 위해서입니다.
// 저장 확정을 '커밋', 처리 중 실패했을 때 DB 변경을 되돌리는 것을 '롤백'이라고 합니다.
// 각 시험이 끝나면 cleanUp이 테스트 전용 DB 데이터를 직접 지웁니다. 개발용 DB는 사용하지 않습니다.
// 여기서 요청자 ID는 시험용 입력입니다. 로그인한 본인인지 확인하는 인증 시험은 아닙니다.

package io.github.greenapple0101.finaccess.transfer;

import io.github.greenapple0101.finaccess.account.Account;
import io.github.greenapple0101.finaccess.account.AccountRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @SpringBootTest는 실제 서비스와 저장소 등 앱의 Spring 구성을 준비합니다.
@SpringBootTest
@Testcontainers
class TransferRequestServiceTest {

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

    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private UUID requesterId;
    private UUID otherCompanyAccountId;

    // 각 시험 전에 회사·계좌·요청자를 새로 준비합니다. 앞 시험의 데이터에 기대지 않도록 합니다.
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

    // 서비스가 커밋한 시험 데이터를 직접 지웁니다. 참조하는 요청서부터 지운 뒤 계좌·사용자·회사를 지웁니다.
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM transfer_requests");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update("DELETE FROM app_users");
        jdbcTemplate.update("DELETE FROM companies");
    }

    // 입금 계좌는 같은 회사여도, 다른 회사여도 요청할 수 있습니다. 두 경우를 반복해서 확인합니다.
    // 요청만 접수하므로 현재 잔액이 0원이어도 저장되고, 잔액은 그대로입니다.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void commitsRequestForSameOrOtherCompanyRecipient(boolean otherCompany) {
        UUID recipientAccountId;
        if (otherCompany) {
            recipientAccountId = otherCompanyAccountId;
        } else {
            recipientAccountId = destinationAccountId;
        }
        TransferRequestService.RequestedTransfer result =
                transferRequestService.request(sourceAccountId, recipientAccountId, 1000L, requesterId);
        // 서비스 호출이 끝난 뒤 새로 조회합니다. 실제로 커밋되어 다른 조회에서도 보이는지 확인합니다.
        TransferRequest savedTransfer = transferRequestRepository.findById(result.id()).orElseThrow();
        assertThat(savedTransfer.getSourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(savedTransfer.getDestinationAccountId()).isEqualTo(recipientAccountId);
        assertThat(savedTransfer.getRequesterId()).isEqualTo(requesterId);
        assertThat(savedTransfer.getAmountWon()).isEqualTo(1000L);
        assertThat(savedTransfer.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(savedTransfer.getCreatedAt()).isNotNull();
        assertThat(result).isEqualTo(new TransferRequestService.RequestedTransfer(
                savedTransfer.getId(), sourceAccountId, recipientAccountId, 1000L, requesterId, TransferStatus.REQUESTED));
        assertThat(accountRepository.findById(sourceAccountId).orElseThrow().getBalanceWon()).isZero();
        assertThat(accountRepository.findById(recipientAccountId).orElseThrow().getBalanceWon()).isZero();
    }

    // 다른 회사의 계좌에서 출금해 달라는 요청은 거절하고, 요청서도 저장하지 않아야 합니다.
    @Test
    void rejectsOtherCompanySourceWithoutSaving() {
        assertThatThrownBy(() -> transferRequestService.request(otherCompanyAccountId, destinationAccountId, 1000L, requesterId))
                .isInstanceOf(TransferCompanyMismatchException.class);
        assertThat(transferRequestRepository.count()).isZero();
    }

    // 출금 계좌·입금 계좌·요청자 중 하나씩 없는 ID로 바꾸어 세 가지 경우를 확인합니다.
    @ParameterizedTest
    @ValueSource(strings = {"source", "destination", "requester"})
    void rejectsMissingReferencesWithoutSaving(String missing) {
        UUID missingId = UUID.randomUUID();
        // 아래 세 값 중 이번 시험에서 없는 것으로 정한 값만 missingId로 고릅니다.
        UUID requestedSourceId;
        if (missing.equals("source")) {
            requestedSourceId = missingId;
        } else {
            requestedSourceId = sourceAccountId;
        }

        UUID requestedDestinationId;
        if (missing.equals("destination")) {
            requestedDestinationId = missingId;
        } else {
            requestedDestinationId = destinationAccountId;
        }

        UUID requestedUserId;
        if (missing.equals("requester")) {
            requestedUserId = missingId;
        } else {
            requestedUserId = requesterId;
        }

        assertThatThrownBy(() -> transferRequestService.request(
                requestedSourceId, requestedDestinationId, 1000L, requestedUserId))
                .isInstanceOf(TransferReferenceNotFoundException.class)
                .hasMessageContaining(missingId.toString());
        assertThat(transferRequestRepository.count()).isZero();
    }

    // ID 누락·같은 계좌·0원·음수 금액을 하나씩 넣어 봅니다.
    // 오류가 났다는 것과 함께, 요청서가 하나도 저장되지 않았다는 것도 확인합니다.
    @ParameterizedTest
    @ValueSource(strings = {"nullSource", "nullDestination", "nullRequester", "sameAccount", "zero", "negative"})
    void rejectsInvalidInputWithoutSaving(String invalid) {
        // 각 값에 정상 입력 또는 이번 시험에서 확인할 잘못된 입력을 넣습니다.
        // 한 값의 준비를 끝내고 다음 값으로 넘어가도록 if 문으로 풀어 씁니다.
        UUID requestedSourceId;
        if (invalid.equals("nullSource")) {
            requestedSourceId = null;
        } else {
            requestedSourceId = sourceAccountId;
        }

        UUID requestedDestinationId;
        if (invalid.equals("nullDestination")) {
            requestedDestinationId = null;
        } else if (invalid.equals("sameAccount")) {
            requestedDestinationId = sourceAccountId;
        } else {
            requestedDestinationId = destinationAccountId;
        }

        UUID requestedUserId;
        if (invalid.equals("nullRequester")) {
            requestedUserId = null;
        } else {
            requestedUserId = requesterId;
        }

        long requestedAmountWon;
        if (invalid.equals("zero")) {
            requestedAmountWon = 0L;
        } else if (invalid.equals("negative")) {
            requestedAmountWon = -1L;
        } else {
            requestedAmountWon = 1000L;
        }

        assertThatThrownBy(() -> transferRequestService.request(
                requestedSourceId, requestedDestinationId, requestedAmountWon, requestedUserId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(transferRequestRepository.count()).isZero();
    }
}
