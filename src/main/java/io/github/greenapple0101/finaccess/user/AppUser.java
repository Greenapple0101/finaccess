// [우리 앱의 사용자와 소속 회사를 담는 파일]
// 예를 들어 "사용자 A는 핀액세스 회사 소속"이라는 업무 정보를 저장합니다.
// 로그인 제공자가 사용하는 사용자 ID와, FinAccess 안에서 사용하는 사용자 ID는 서로 구분합니다.
// 읽는 순서: ID·회사·로그인 연결값 확인 → 생성자의 입력 검사 → 값을 꺼내는 getter.
// 저장 흐름: 기존 회사와 연결값으로 객체 만들기 → AppUserRepository로 저장 → 필요할 때 조회.
//
// identityIssuer는 신원을 발급한 곳, identitySubject는 그곳에서 정한 사용자 식별값입니다.
// 예를 들어 발급자 X의 사용자 "123"과 발급자 Y의 사용자 "123"은 서로 다른 사람일 수 있습니다.
// 그래서 발급자와 사용자 식별값을 한 쌍으로 보관합니다. 바뀔 수 있는 이메일을 연결 기준으로 쓰지 않습니다.
// 동일한 한 쌍을 두 번 등록하지 못하도록 V2 SQL의 UNIQUE(중복 금지) 제약도 적용했습니다.
//
// 현재 사용자는 회사 한 곳에 속합니다. 비밀번호나 토큰 원문은 이 객체에 넣지 않습니다.
// Keycloak 로그인은 아직 연결하지 않았습니다. 여기에 문자열을 넣는 것만으로 본인 확인이 되지는 않습니다.
// 나중에 토큰이 올바른지 검증한 결과로 이 연결값을 조회해야 합니다.

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

// JPA가 DB의 사용자 데이터와 이 객체를 연결하도록 하는 표시입니다.
// 사용자마다 따로 생기는 데이터 객체이며, 업무 Service처럼 공유해서 쓰는 빈과는 역할이 다릅니다.
@Entity
// app_users 테이블의 한 행이 우리 앱의 사용자 한 명을 나타냅니다.
@Table(name = "app_users")
public class AppUser {

    // FinAccess 내부에서 사용자를 구별하는 UUID입니다. 아래 identitySubject와는 다른 값입니다.
    // 새 사용자를 저장할 때 Hibernate가 자동으로 만듭니다.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 여러 사용자(Many)가 회사 하나(One)에 속할 수 있어 @ManyToOne입니다.
    // DB에는 회사 객체를 통째로 복사하지 않고 company_id에 회사 ID를 저장해서 연결합니다.
    // @JoinColumn은 그 연결 열의 이름, optional=false와 nullable=false는 회사가 필수라는 설정입니다.
    //
    // LAZY는 회사 상세 정보를 필요할 때 읽도록 하는 설정입니다. 이를 지연 로딩이라고 합니다.
    // 예를 들어 사용자 조회 후 회사 이름을 읽는 순간 추가 조회가 일어날 수 있습니다.
    // 아직 회사 정보를 읽지 않았는데 JPA의 관리가 끝났다면 나중에 읽으려 할 때 오류가 날 수 있습니다.
    // updatable=false는 JPA가 소속 회사를 수정하는 SQL을 만들지 않게 하며, 권한 검사는 아닙니다.
    // 사용자를 저장할 때 새 회사까지 자동 저장하도록 설정하지 않았으므로 기존 회사를 연결합니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    // 신원을 발급한 곳을 나타냅니다. 예: https://identity.example/realms/finaccess.
    // @Column은 DB 열 이름·길이·수정 여부를 알려 주는 설정이지, 실제 발급자를 확인하는 보안 기능은 아닙니다.
    @Column(name = "identity_issuer", nullable = false, length = 512, updatable = false)
    private String identityIssuer;

    // 발급자가 정한 사용자의 식별값입니다. 숫자나 UUID처럼 보여도 문자열로 그대로 보관합니다.
    // issuer와 subject는 사용자를 연결하는 기준이므로 임의로 소문자로 바꾸거나 공백을 잘라내지 않습니다.
    @Column(name = "identity_subject", nullable = false, length = 255, updatable = false)
    private String identitySubject;

    // 생성 시각은 DB의 기본값이 넣습니다. Java는 저장·수정 SQL에서 이 열을 제외합니다.
    // 저장 직후 객체에 값이 자동으로 복사되지는 않습니다. DB에서 새로 읽거나 refresh한 뒤 확인합니다.
    // refresh는 현재 객체의 값을 DB에 저장된 값으로 다시 채우는 기능입니다.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // DB 조회 결과로 객체를 만들 때 JPA가 사용하는 빈 생성자입니다.
    // 새 사용자를 만드는 업무 코드는 아래처럼 필요한 값을 받는 생성자를 사용합니다.
    protected AppUser() {
    }

    // 회사와 연결값을 받은 뒤 검사하고 객체에 보관합니다.
    // 여기서는 회사 객체가 null인지 검사할 뿐, DB에 실제로 있는 회사인지 조회하지는 않습니다.
    // 존재하지 않는 회사를 가리키는 저장은 DB의 외래키 제약(연결 대상이 있어야 한다는 규칙)으로 막습니다.
    public AppUser(Company company, String identityIssuer, String identitySubject) {
        if (company == null) {
            throw new IllegalArgumentException("Company must be provided");
        }
        // 발급자 값은 최대 512, 사용자 식별값은 최대 255까지 허용하고 빈 값은 거부합니다.
        // 동일한 검사를 반복해서 쓰지 않으려고 아래 공통 메서드에 값·최대 길이·항목 이름을 전달합니다.
        validateIdentityValue(identityIssuer, 512, "Identity issuer");
        validateIdentityValue(identitySubject, 255, "Identity subject");
        this.company = company;
        this.identityIssuer = identityIssuer;
        this.identitySubject = identitySubject;
    }

    // 두 연결값에 함께 쓰는 내부 검사입니다. label은 오류 설명에 넣을 항목 이름입니다.
    // null은 값 없음, isBlank()는 빈 문자열이나 공백만 있는 문자열을 뜻합니다.
    // codePointCount는 유니코드 코드 포인트 수를 셉니다. 조합 이모지 등은 눈에 보이는 글자 수와 다를 수 있습니다.
    // 조건 중 하나라도 맞으면 예외를 보내 생성 과정을 중단합니다.
    private static void validateIdentityValue(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(label + " must be non-blank and at most " + maxLength + " characters");
        }
    }

    // 아래 getter들은 담아 둔 값을 꺼내는 통로입니다. 예: getIdentitySubject()는 로그인 연결값을 반환합니다.
    // getCompany()로 받은 회사의 상세 정보를 읽을 때는 지연 로딩으로 추가 DB 조회가 일어날 수 있습니다.
    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public String getIdentityIssuer() { return identityIssuer; }
    public String getIdentitySubject() { return identitySubject; }
    public Instant getCreatedAt() { return createdAt; }
}
