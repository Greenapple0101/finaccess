// [이 파일의 역할: FinAccess 업무 사용자와 회사 소속]
// Keycloak 로그인 계정과 우리 앱의 업무 사용자를 구분하기 위해 만들었습니다.
// Keycloak은 인증을, 이 테이블은 "누가 어느 회사 소속인가"라는 업무 정보를 담당합니다.
// 비밀번호나 토큰 원문은 저장하지 않습니다.
//
// id는 FinAccess 내부 사용자 UUID입니다.
// identityIssuer는 신원을 발급한 곳, identitySubject는 그 발급자 안의 사용자 ID입니다.
// 같은 subject라도 issuer가 다르면 다른 신원일 수 있으므로 두 값을 묶어 식별합니다.
// 이메일·표시 이름은 바뀔 수 있어 연결 식별자로 쓰지 않습니다.
//
// 현재 사용자 한 명은 회사 한 곳에 소속됩니다. 여러 사용자에게 같은 회사를 연결할 수 있습니다.
// 동일 issuer·subject를 회사만 바꾸어 재등록하는 것은 V2의 UNIQUE 제약이 막습니다.
// 이 모델에 문자열을 넣었다고 로그인 검증이 되는 것은 아닙니다.
// 향후 신뢰할 발급자의 토큰을 검증한 결과로 사용자를 찾아야 합니다.
// 지금은 공개 사용자 등록 API나 로그인 처리 코드가 없습니다.

// package는 이 파일의 소속 주소입니다. 계층별 호출 순서를 나타내는 문법은 아닙니다.
// 아래 import는 다른 패키지의 타입을 짧은 이름으로 쓰기 위한 선언이며, 실행이나 객체 생성이 아닙니다.
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

// jakarta.persistence의 어노테이션입니다. JPA 매핑 대상임을 표시합니다.
// @Service처럼 컴포넌트 탐색으로 업무 객체 하나를 공유 빈으로 등록하는 표시가 아닙니다.
// JPA 구현체 Hibernate가 이 메타데이터를 읽고 필드와 DB 컬럼을 연결합니다.
@Entity
// 실제 테이블 이름입니다. Java 클래스 이름과 DB 테이블 이름을 명시적으로 연결합니다.
@Table(name = "app_users")
public class AppUser {

    // @Id가 필드에 붙어 있으므로 JPA는 이 클래스에서 필드 접근 방식으로 매핑합니다.
    // private 필드여도 프레임워크가 매핑 정보를 이용해 값을 읽고 채울 수 있습니다.
    // @GeneratedValue(UUID)는 Hibernate가 영속화 시 UUID 식별자를 생성하도록 합니다.
    // DB의 자동 증가 숫자나 실제 은행 계좌번호를 뜻하지 않습니다.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 관계 방향은 현재 객체 → Company입니다. 여러 사용자/계좌가 회사 하나를 참조할 수 있습니다.
    // @ManyToOne만으로 Company에 사용자/계좌 목록 필드가 자동 생기지는 않습니다.
    // fetch=LAZY는 회사 정보를 필요 시 읽도록 요청합니다. 프록시를 통해 로딩될 수 있습니다.
    // 회사 ID만 읽을 때와 회사명 등 상세 속성을 읽을 때의 SQL 동작이 다를 수 있습니다.
    // 트랜잭션 밖에서 초기화되지 않은 관계를 읽으면 지연 로딩 오류가 발생할 수 있습니다.
    // optional=false는 관계 필수, JoinColumn은 외래키 컬럼 이름을 지정합니다.
    // updatable=false는 현재 JPA에서 소속 변경 UPDATE를 하지 않겠다는 뜻이며 보안 권한은 아닙니다.
    // cascade를 지정하지 않아 이 객체를 저장한다고 새 회사까지 자동 저장하도록 구성하지 않았습니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    // 신원 연결키의 발급자 부분입니다. 예: https://identity.example/realms/finaccess.
    // @Column 이름·길이 정보는 DB 매핑용이며 토큰 서명 검증을 수행하지 않습니다.
    // 발급자와 subject의 조합을 유일하게 하는 제약은 V2 SQL에 있습니다.
    @Column(name = "identity_issuer", nullable = false, length = 512, updatable = false)
    private String identityIssuer;

    // 발급자 안에서 사용자를 식별하는 값입니다. UUID 모양일 수 있어도 문자열로 보관합니다.
    // 사용자가 주장하는 임의 문자열을 곧바로 신뢰하는 API는 아직 만들지 않았습니다.
    @Column(name = "identity_subject", nullable = false, length = 255, updatable = false)
    private String identitySubject;

    // createdAt(Java) ↔ created_at(DB) 이름을 연결합니다.
    // insertable=false: JPA INSERT에서 이 컬럼을 생략합니다.
    // updatable=false: JPA UPDATE에서 이 컬럼을 생략합니다.
    // 그래서 DB DEFAULT CURRENT_TIMESTAMP가 처음 값을 넣습니다.
    // nullable=false는 매핑 정보이며, 실제 NOT NULL 제약은 마이그레이션 SQL에도 있습니다.
    // 저장한 Java 객체에 값이 자동 복사되는 매핑은 아니므로 재조회나 refresh 후 확인합니다.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // JPA가 조회 결과를 객체로 복원할 때 사용하는 인자 없는 생성자입니다.
    // protected는 같은 패키지 및 상속 관련 접근을 허용하며 public보다 좁은 범위입니다.
    // 일반 업무 코드에는 아래의 값이 있는 생성자를 사용하도록 의도를 표현합니다.
    // JPA의 객체 복원은 업무용 생성자의 검증을 그대로 다시 수행하는 것과 다릅니다.
    protected AppUser() {
    }

    // 회사와 신원 식별값을 전달받아 업무 사용자 객체를 만듭니다.
    // company==null은 검사하지만 DB에 저장된 회사인지 여기서 SQL로 확인하지는 않습니다.
    // 저장 시 외래키가 존재하지 않는 회사 연결을 거부합니다.
    // 문자열을 임의로 소문자 변환·trim하지 않고 식별값을 보존합니다.
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

    // 두 필드에서 공통으로 쓰는 입력 검사입니다. private은 내부용, static은 객체 상태에 의존하지 않음입니다.
    // label은 예외 메시지에 넣을 필드 설명, maxLength는 허용 길이입니다.
    // 문자열을 +로 연결해 어떤 입력이 잘못됐는지 메시지를 구성합니다.
    private static void validateIdentityValue(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(label + " must be non-blank and at most " + maxLength + " characters");
        }
    }

    // getter는 값을 읽는 메서드입니다. 이름이 get으로 시작한다고 DB 조회가 자동 실행되는 것은 아닙니다.
    // 반환 타입 UUID/String/long/Instant는 호출자가 돌려받을 값의 종류를 뜻합니다.
    // 중괄호 안 return 필드는 현재 객체가 보관한 값을 반환합니다.
    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public String getIdentityIssuer() { return identityIssuer; }
    public String getIdentitySubject() { return identitySubject; }
    public Instant getCreatedAt() { return createdAt; }
}
