package io.github.greenapple0101.finaccess.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// JPA가 DB와 연결해 관리할 엔티티라는 표시입니다.
// @Entity를 붙이거나 new로 객체를 만든다고 즉시 DB에 저장되는 것은 아닙니다.
@Entity
// 이 Java 클래스가 연결될 실제 DB 테이블 이름입니다. 테이블 생성은 Flyway SQL이 담당합니다.
@Table(name = "companies")
public class Company {

    // DB 기본키와 연결되는 필드입니다.
    // GeneratedValue의 UUID 전략은 영속화 시 Hibernate가 UUID를 생성하게 합니다.
    // 객체를 생성한 직후에는 id가 null일 수 있습니다.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // name 필드와 name 컬럼을 연결합니다. 최대 100자, NULL 불가라는 매핑 정보입니다.
    // @Column 자체가 요청을 검사하는 것은 아닙니다. 아래 생성자와 DB 제약이 각각 검사합니다.
    @Column(nullable = false, length = 100)
    private String name;

    // DB가 DEFAULT CURRENT_TIMESTAMP로 생성 시각을 채웁니다.
    // insertable/updatable=false이므로 JPA의 INSERT/UPDATE에는 이 컬럼을 넣지 않습니다.
    // 이 매핑은 DB 생성값을 저장 직후 객체에 자동 반영하지 않으므로 다시 읽거나 refresh해야 합니다.
    // Instant는 시간대와 독립적인 한 시점을 표현합니다.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // JPA가 DB 조회 결과로 객체를 복원할 때 사용할 기본 생성자입니다.
    // protected로 두어 일반 사용 코드에서는 값이 있는 생성자를 사용하도록 유도합니다.
    protected Company() {
        // JPA uses this constructor when loading an entity.
    }

    // 회사 객체를 만드는 진입점입니다. 저장 전에 유효하지 않은 이름을 거부합니다.
    // ||는 또는(OR), == null은 값 자체가 없다는 검사, isBlank는 빈 문자열과 공백 검사입니다.
    // codePointCount는 유니코드 코드 포인트 수를 세어 문자열 길이를 확인합니다.
    // throw는 정상 진행을 멈추고 호출한 쪽으로 예외를 전달합니다.
    public Company(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name must not be blank");
        }
        if (name.codePointCount(0, name.length()) > 100) {
            throw new IllegalArgumentException("Company name must not exceed 100 characters");
        }
        this.name = name;
    }

    // 아래 getter들은 객체의 값을 읽는 메서드입니다. return은 값을 호출자에게 돌려줍니다.
    // 임의 변경용 setter는 현재 기능에 필요하지 않아 만들지 않았습니다.
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
