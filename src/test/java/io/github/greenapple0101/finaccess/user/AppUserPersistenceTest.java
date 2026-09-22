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

package io.github.greenapple0101.finaccess.user;

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

// 사용자와 회사의 관계를 실제 PostgreSQL에서 검증하는 통합 테스트입니다.
// 클래스의 Transactional은 각 테스트를 트랜잭션으로 실행하고 종료 후 기본적으로 롤백합니다.
// 제약 위반 테스트는 예외를 예상하는 테스트이며 실패 로그가 보여도 의도한 결과일 수 있습니다.
@SpringBootTest
@Testcontainers
@Transactional
class AppUserPersistenceTest {
    private static final String ISSUER = "https://identity.example/realms/finaccess";

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
    @Autowired AppUserRepository users;
    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired CompanyRepository companies;
    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired EntityManager entityManager;
    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired JdbcTemplate jdbc;

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 같은 회사에 사용자 둘을 저장한 후 영속성 컨텍스트를 비우고 다시 읽습니다.
    // 사용자의 외래키, 연결된 회사명, DB 생성 시각을 함께 확인합니다.
    void multipleUsersCanBelongToOneCompanyAndBeReloadedByIdentity() {
        Company company = companies.saveAndFlush(new Company("테스트 회사"));
        AppUser first = users.saveAndFlush(new AppUser(company, ISSUER, "user-1"));
        users.saveAndFlush(new AppUser(company, ISSUER, "user-2"));
        // 메모리의 관리 객체를 분리합니다. DB 행을 삭제하는 명령은 아닙니다.
        entityManager.clear();

        AppUser loaded = users.findByIdentityIssuerAndIdentitySubject(ISSUER, "user-1").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(first.getId());
        assertThat(loaded.getCompany().getId()).isEqualTo(company.getId());
        assertThat(loaded.getCompany().getName()).isEqualTo("테스트 회사");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(users.findByIdentityIssuerAndIdentitySubject(ISSUER, "user-2")).isPresent();
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 같은 issuer·subject 조합으로 회사만 바꾸어 중복 등록할 수 없음을 검증합니다.
    // saveAndFlush로 제약 검사가 메서드 호출 중 발생하도록 합니다.
    void sameIdentityCannotBeRegisteredInAnotherCompany() {
        Company first = companies.saveAndFlush(new Company("회사 A"));
        Company second = companies.saveAndFlush(new Company("회사 B"));
        users.saveAndFlush(new AppUser(first, ISSUER, "user-1"));

        assertThatThrownBy(() -> users.saveAndFlush(new AppUser(second, ISSUER, "user-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // subject가 같아도 발급자가 다르면 다른 신원입니다. 두 사용자를 구분하는지 확인합니다.
    void sameSubjectFromDifferentIssuersRemainsDistinct() {
        Company company = companies.saveAndFlush(new Company("테스트 회사"));
        AppUser first = users.saveAndFlush(new AppUser(company, ISSUER, "user-1"));
        AppUser second = users.saveAndFlush(new AppUser(company, "https://other.example/realms/finaccess", "user-1"));
        // 메모리의 관리 객체를 분리합니다. DB 행을 삭제하는 명령은 아닙니다.
        entityManager.clear();

        assertThat(users.findByIdentityIssuerAndIdentitySubject(ISSUER, "user-1").orElseThrow().getId())
                .isEqualTo(first.getId()).isNotEqualTo(second.getId());
        assertThat(users.findByIdentityIssuerAndIdentitySubject(ISSUER, "missing")).isEmpty();
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 직접 SQL로 없는 company_id를 넣어 외래키가 거부하는지 확인합니다.
    // Java 검사만 우회하면 잘못된 데이터가 저장되는 구조가 아닌지 검증합니다.
    void databaseRejectsNonexistentCompany() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_users (id, company_id, identity_issuer, identity_subject) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), ISSUER, "user-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
