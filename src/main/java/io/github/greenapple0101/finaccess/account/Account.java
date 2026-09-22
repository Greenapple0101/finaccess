// [이 파일의 역할: 계좌 상태와 입출금 규칙]
// accounts 테이블과 연결되며 회사 소유 계좌 하나를 표현합니다.
// 필드는 내부 UUID, 소유 회사, 원 단위 잔액, 생성 시각입니다.
// 실제 은행 계좌번호·외화·이자 계산은 아직 모델에 없습니다.
//
// 이 파일이 스스로 HTTP 요청을 받거나 DB 연결을 열지는 않습니다.
// 생성자와 deposit/withdraw는 Java 객체의 상태를 만들고 변경합니다.
// 새 객체는 Repository로 저장해야 하고, 관리 중인 엔티티는 변경 감지가 가능합니다.
// 동시 요청 잠금, 거래 원장, 계좌 간 이체 원자성은 아직 별도 구현이 필요합니다.
//
// 생성 → 0원. 입금 → 양수 검사 후 안전한 덧셈. 출금 → 양수·잔액 검사 후 차감.
// 잔액을 아무 값으로 덮어쓰는 setter를 제공하지 않아 변경 통로를 제한했습니다.
// 이것만으로 외부 SQL 변경이나 모든 동시성 문제가 차단되지는 않습니다.

// package는 이 파일의 소속 주소입니다. 계층별 호출 순서를 나타내는 문법은 아닙니다.
// 아래 import는 다른 패키지의 타입을 짧은 이름으로 쓰기 위한 선언이며, 실행이나 객체 생성이 아닙니다.
package io.github.greenapple0101.finaccess.account;

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
@Table(name = "accounts")
public class Account {

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

    // 원 단위 long 정수입니다. 예: 1000L은 1,000원, L은 long 리터럴 접미사입니다.
    // long은 64비트 부호 있는 정수로 상한이 있습니다. 무한한 정밀도의 숫자가 아닙니다.
    // double/float의 소수 근사 문제를 피하지만, 정수도 범위 초과를 별도로 검사해야 합니다.
    // 원화 정수만 지원하는 현재 범위이며 외화·소수·반올림 정책은 아직 없습니다.
    @Column(name = "balance_won", nullable = false)
    private long balanceWon;

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
    protected Account() {
    }

    // 회사 참조를 받아 새 계좌를 만듭니다. 잔액을 입력받는 인자가 없어 초기 잔액은 항상 0원입니다.
    // this는 지금 만드는 Account 객체를 가리킵니다. 검증 뒤 필드를 설정합니다.
    public Account(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("Company must be provided");
        }
        this.company = company;
        this.balanceWon = 0L;
    }

    // 입금은 객체 잔액을 바꾸므로 반환값 없이 void입니다.
    // requirePositiveAmount를 먼저 호출해 0원과 음수 요청을 거부합니다.
    // Math.addExact는 정수 범위를 넘으면 ArithmeticException을 던집니다.
    // 대입문은 오른쪽 계산이 성공해야 왼쪽 값을 바꾸므로 실패 시 잔액이 유지됩니다.
    // 예: 잔액 1000, 입금 300 → 1300. 잔액 MAX_VALUE, 입금 1 → 예외, 기존 잔액 유지.
    public void deposit(long amountWon) {
        requirePositiveAmount(amountWon);
        balanceWon = Math.addExact(balanceWon, amountWon);
    }

    // 출금 순서: 양수 검사 → 잔액 부족 검사 → 차감.
    // 잔액보다 많은지 먼저 검사하므로 실패하면서 이미 돈이 빠지는 문제를 피합니다.
    // balanceWon -= amountWon은 balanceWon = balanceWon - amountWon의 의미입니다.
    // 잔액과 같은 금액은 허용하며 결과는 0입니다.
    // 이는 한 객체의 규칙입니다. 동시 요청 두 개가 같은 잔액을 읽는 문제까지 해결하지는 않습니다.
    public void withdraw(long amountWon) {
        requirePositiveAmount(amountWon);
        if (amountWon > balanceWon) {
            throw new InsufficientBalanceException();
        }
        balanceWon -= amountWon;
    }

    // 입금과 출금이 공유하는 보조 함수입니다. 외부 API가 아닙니다.
    // 0원도 의미 없는 거래로 취급해 amountWon <= 0을 거부합니다.
    // 이 함수를 통과했다고 잔액 부족이나 오버플로 검사까지 끝난 것은 아닙니다.
    private static void requirePositiveAmount(long amountWon) {
        if (amountWon <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }


    // getter는 값을 읽는 메서드입니다. 이름이 get으로 시작한다고 DB 조회가 자동 실행되는 것은 아닙니다.
    // 반환 타입 UUID/String/long/Instant는 호출자가 돌려받을 값의 종류를 뜻합니다.
    // 중괄호 안 return 필드는 현재 객체가 보관한 값을 반환합니다.
    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public long getBalanceWon() { return balanceWon; }
    public Instant getCreatedAt() { return createdAt; }
}
