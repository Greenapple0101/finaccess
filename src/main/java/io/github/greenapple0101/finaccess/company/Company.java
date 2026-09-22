// [이 파일의 역할: 회사 데이터를 표현하는 JPA 엔티티]
// companies 테이블의 한 행과 연결되는 객체의 모양입니다.
// 클래스는 정의, new Company("핀액세스")는 그 정의로 만든 개별 객체입니다.
// new만 실행하면 메모리에 객체가 생길 뿐 DB 행이 생성되지는 않습니다.
//
// 필드 id/name/createdAt은 이 객체의 상태, 생성자는 최초 상태를 만드는 코드,
// getter는 상태를 읽는 메서드입니다. 회사 이름 규칙은 생성자에 있습니다.
// 엔티티 객체는 CompanyService처럼 @Service로 등록하는 공유 빈이 아닙니다.
// 저장·조회한 엔티티는 JPA 영속성 컨텍스트의 관리 대상이 될 수 있습니다.
//
// 테이블 생성은 V1 SQL(Flyway), 객체와 테이블 매핑은 이 클래스(JPA)입니다.
// @Column만 바꾸어도 이미 적용된 DB 테이블이 자동 변경되지는 않습니다.
// 이 프로젝트는 ddl-auto=validate를 사용하므로 실제 구조 변경은 새 마이그레이션으로 합니다.

// package는 이 파일의 소속 주소입니다. 계층별 호출 순서를 나타내는 문법은 아닙니다.
// 아래 import는 다른 패키지의 타입을 짧은 이름으로 쓰기 위한 선언이며, 실행이나 객체 생성이 아닙니다.
package io.github.greenapple0101.finaccess.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// jakarta.persistence의 어노테이션입니다. JPA 매핑 대상임을 표시합니다.
// @Service처럼 컴포넌트 탐색으로 업무 객체 하나를 공유 빈으로 등록하는 표시가 아닙니다.
// JPA 구현체 Hibernate가 이 메타데이터를 읽고 필드와 DB 컬럼을 연결합니다.
@Entity
// 실제 테이블 이름입니다. Java 클래스 이름과 DB 테이블 이름을 명시적으로 연결합니다.
@Table(name = "companies")
public class Company {

    // @Id가 필드에 붙어 있으므로 JPA는 이 클래스에서 필드 접근 방식으로 매핑합니다.
    // private 필드여도 프레임워크가 매핑 정보를 이용해 값을 읽고 채울 수 있습니다.
    // @GeneratedValue(UUID)는 Hibernate가 영속화 시 UUID 식별자를 생성하도록 합니다.
    // DB의 자동 증가 숫자나 실제 은행 계좌번호를 뜻하지 않습니다.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 최대 100자, NULL 불가라는 컬럼 매핑 정보입니다.
    // 어노테이션의 length만으로 HTTP 입력 길이가 자동 검사되는 것은 아닙니다.
    // 아래 생성자가 Java 규칙을 검사하고 DB에도 SQL 제약이 있습니다.
    @Column(nullable = false, length = 100)
    private String name;

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
    protected Company() {
    }

    // 생성자 호출 예: new Company("핀액세스"). 오른쪽 문자열이 name 매개변수에 들어옵니다.
    // null은 값 자체가 없음, 빈 문자열은 길이 0, 공백 문자열은 내용이 공백인 경우입니다.
    // ||는 OR이며 왼쪽이 참이면 오른쪽을 실행하지 않는 단락 평가를 합니다.
    // name==null일 때 isBlank를 호출하지 않으므로 NullPointerException을 피합니다.
    // codePointCount는 유니코드 코드 포인트 개수를 셉니다. 사용자 눈의 글자 수와 항상 같은 개념은 아닙니다.
    // throw new ... 는 예외 객체를 생성해 정상 실행을 중단하고 호출자에게 전달합니다.
    public Company(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name must not be blank");
        }
        if (name.codePointCount(0, name.length()) > 100) {
            throw new IllegalArgumentException("Company name must not exceed 100 characters");
        }
        // 검사가 모두 통과한 후 객체의 name 필드에 입력값을 저장합니다.
        // 왼쪽 this.name은 필드, 오른쪽 name은 생성자 매개변수입니다. DB INSERT 문은 아닙니다.
        this.name = name;
    }

    // getter는 값을 읽는 메서드입니다. 이름이 get으로 시작한다고 DB 조회가 자동 실행되는 것은 아닙니다.
    // 반환 타입 UUID/String/long/Instant는 호출자가 돌려받을 값의 종류를 뜻합니다.
    // 중괄호 안 return 필드는 현재 객체가 보관한 값을 반환합니다.
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
