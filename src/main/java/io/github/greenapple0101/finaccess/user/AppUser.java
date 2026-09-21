package io.github.greenapple0101.finaccess.user;

import io.github.greenapple0101.finaccess.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// JPA가 DB와 연결해 관리할 엔티티라는 표시입니다.
// @Entity를 붙이거나 new로 객체를 만든다고 즉시 DB에 저장되는 것은 아닙니다.
@Entity
// 이 Java 클래스가 연결될 실제 DB 테이블 이름입니다. 테이블 생성은 Flyway SQL이 담당합니다.
@Table(name = "app_users")
public class AppUser {

    // DB 기본키와 연결되는 필드입니다.
    // GeneratedValue의 UUID 전략은 영속화 시 Hibernate가 UUID를 생성하게 합니다.
    // 객체를 생성한 직후에는 id가 null일 수 있습니다.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // [읽기 6] 여러 업무 사용자가 같은 회사에 속할 수 있는 다대일 관계입니다.
    // LAZY는 관계를 필요 시 로딩하도록 요청합니다. 트랜잭션 밖에서는 로딩 문제가 생길 수 있습니다.
    // JoinColumn은 이 관계가 company_id 외래키에 저장된다는 뜻입니다.
    // optional/nullable=false는 회사 소속이 필수임을 표현합니다.
    // updatable=false는 현재 모델에서 소속 변경 UPDATE를 하지 않는다는 뜻이며 DB 권한 설정은 아닙니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    // 로그인 계정을 연결하는 두 필드는 issuer와 subject입니다.
    // issuer는 인증 발급자, subject는 그 발급자 안의 사용자 식별자입니다.
    // 이메일은 바뀔 수 있어 식별자로 쓰지 않습니다. DB는 두 필드 조합의 중복을 막습니다.
    // 이 모델만으로 로그인 검증이 되지는 않습니다. 검증된 토큰과의 연결은 이후 구현합니다.
    @Column(name = "identity_issuer", nullable = false, length = 512, updatable = false)
    private String identityIssuer;

    @Column(name = "identity_subject", nullable = false, length = 255, updatable = false)
    private String identitySubject;

    // DB가 DEFAULT CURRENT_TIMESTAMP로 생성 시각을 채웁니다.
    // insertable/updatable=false이므로 JPA의 INSERT/UPDATE에는 이 컬럼을 넣지 않습니다.
    // 이 매핑은 DB 생성값을 저장 직후 객체에 자동 반영하지 않으므로 다시 읽거나 refresh해야 합니다.
    // Instant는 시간대와 독립적인 한 시점을 표현합니다.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // JPA가 DB 조회 결과로 객체를 복원할 때 사용할 기본 생성자입니다.
    // protected로 두어 일반 사용 코드에서는 값이 있는 생성자를 사용하도록 유도합니다.
    protected AppUser() {
    }

    // 새 업무 사용자를 구성합니다. 저장된 회사 객체와 인증 식별 정보를 받습니다.
    // 비밀번호나 토큰 원문은 저장하지 않습니다. 한 사용자는 회사 한 곳에 소속됩니다.
    public AppUser(Company company, String identityIssuer, String identitySubject) {
        if (company == null) {
            throw new IllegalArgumentException("Company must be provided");
        }
        validateIdentityValue(identityIssuer, 512, "Identity issuer");
        validateIdentityValue(identitySubject, 255, "Identity subject");
        this.company = company;
        this.identityIssuer = identityIssuer;
        this.identitySubject = identitySubject;
    }

    // issuer와 subject에 공통으로 필요한 빈 값·최대 길이 검사를 모은 보조 메서드입니다.
    // static은 특정 AppUser 객체의 상태를 사용하지 않는 함수라는 의미로 사용했습니다.
    // 인증 식별값을 임의로 trim하거나 소문자로 바꾸면 다른 계정과 혼동될 수 있어 원문을 보존합니다.
    private static void validateIdentityValue(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(label + " must be non-blank and at most " + maxLength + " characters");
        }
    }

    // 값을 읽는 getter들입니다. getCompany로 얻은 지연 로딩 객체의 상세 필드를 읽을 때 SQL이 실행될 수 있습니다.
    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public String getIdentityIssuer() { return identityIssuer; }
    public String getIdentitySubject() { return identitySubject; }
    public Instant getCreatedAt() { return createdAt; }
}
