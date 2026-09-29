// [회사 한 곳의 정보를 담는 파일]
// 예를 들어 회사 이름이 "핀액세스"라면, 그 회사의 ID·이름·등록 시각을 한 객체에 담습니다.
// 객체는 프로그램이 실행되는 동안 메모리에 두고 쓰는 데이터입니다.
// DB는 프로그램이 꺼져도 데이터를 보관하는 곳입니다.
//
// 읽는 순서: 어떤 값을 담는지 확인 → 생성자의 이름 검사 → 값을 꺼내는 getter.
// 실제 사용: new Company("핀액세스")로 만들기 → Repository로 저장 → 나중에 다시 조회.
// new만 실행하면 객체가 생길 뿐 DB에는 아직 저장되지 않습니다.
//
// 이런 객체와 DB의 한 행을 연결하는 기술이 JPA이고, 연결 대상 객체를 엔티티라고 부릅니다.
// 회사 한 곳마다 객체가 따로 생기므로 모든 회사가 공유하는 Service 객체와는 역할이 다릅니다.
// 테이블 자체는 V1 SQL로 만들었습니다. 이 파일의 @Column을 고쳐도 테이블이 자동 변경되지는 않습니다.

package io.github.greenapple0101.finaccess.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// @로 시작하는 표시는 어노테이션입니다. JPA에 이 클래스의 사용 방법을 알려 줍니다.
// @Entity는 "이 클래스의 객체를 DB 데이터와 연결해 주세요"라는 표시입니다.
// 실제로 연결 작업을 하는 JPA 구현 도구는 Hibernate입니다.
@Entity
// 이 클래스가 연결될 DB 테이블 이름은 companies입니다.
// 테이블을 표로 생각하면 회사 한 곳이 한 행, 이름·ID 같은 항목이 각 열입니다.
@Table(name = "companies")
public class Company {

    // @Id는 회사를 구별하는 고유 ID라는 표시입니다. 이름이 같은 회사도 ID로 구별합니다.
    // @GeneratedValue는 저장할 때 Hibernate가 UUID 형태의 ID를 만들어 주도록 합니다.
    // UUID는 550e8400-e29b-41d4-a716-446655440000처럼 생긴 식별값입니다.
    // 여기처럼 어노테이션을 필드에 붙이면 JPA가 private 필드도 읽고 채울 수 있습니다.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // name 필드를 DB의 name 열에 연결합니다. nullable=false는 빈 값(null)을 허용하지 않는다는 뜻입니다.
    // length=100은 DB 열의 길이 정보입니다. 이것만으로 HTTP 입력을 검사하지는 않습니다.
    // 잘못된 이름은 아래 생성자가 검사하고, DB에도 별도의 제약 조건이 있습니다.
    @Column(nullable = false, length = 100)
    private String name;

    // 등록 시각은 Java가 정하지 않고 DB가 현재 시각을 넣습니다.
    // insertable=false와 updatable=false는 JPA의 저장·수정 SQL에서 이 열을 빼라는 뜻입니다.
    // 따라서 저장 직후 이 객체의 createdAt에는 아직 null이 남을 수 있습니다.
    // DB에서 실제로 새로 읽거나 refresh(현재 객체를 DB 값으로 다시 채우기)한 뒤 확인합니다.
    // Instant는 한 시점을 나타내는 타입입니다. 화면에 한국 시간으로 표시하는 일은 별도입니다.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // DB에서 회사를 읽을 때 JPA가 사용할 빈 생성자입니다.
    // JPA는 이 생성자로 객체를 만든 뒤 DB에서 읽은 값을 필드에 채웁니다.
    // 새 회사를 만드는 우리 코드는 아래의 Company(String name)을 사용합니다.
    protected Company() {
    }

    // 생성자는 new Company("핀액세스")를 실행할 때 처음 호출되는 코드입니다.
    // 전달한 "핀액세스"가 아래 name에 들어갑니다. 이름 검사가 끝나야 객체가 완성됩니다.
    public Company(String name) {
        // null은 이름을 아예 받지 못한 경우입니다. isBlank()는 ""나 "   "처럼 내용이 없는지 검사합니다.
        // ||는 "둘 중 하나라도 맞으면"입니다. null이면 뒤의 isBlank()를 호출하지 않아 오류를 피합니다.
        // throw는 문제가 있다는 신호(예외)를 보내고 정상 처리를 멈춥니다.
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name must not be blank");
        }
        // 길이가 100을 넘으면 거부합니다. codePointCount는 유니코드 코드 포인트 수를 셉니다.
        // 일반 한글·영문은 한 글자씩 세지만, 조합된 이모지 등은 눈에 보이는 글자 수와 다를 수 있습니다.
        if (name.codePointCount(0, name.length()) > 100) {
            throw new IllegalArgumentException("Company name must not exceed 100 characters");
        }
        // this.name은 이 회사 객체가 보관할 자리, 오른쪽 name은 방금 전달받은 이름입니다.
        // 검사를 통과한 이름을 객체에 담는 줄이며, 아직 DB 저장 명령은 아닙니다.
        this.name = name;
    }

    // 아래 getter들은 객체에 담긴 값을 꺼내는 메서드입니다.
    // 예를 들어 company.getName()은 이 회사 객체가 가진 이름을 돌려줍니다.
    // 별도로 이름을 덮어쓰는 setter는 만들지 않았습니다.
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
