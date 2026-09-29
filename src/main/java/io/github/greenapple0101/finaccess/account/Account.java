// [계좌 한 개의 정보와 입출금 규칙]
// 예: A회사 계좌의 잔액이 1,000원일 때 300원을 입금하면 1,300원이 됩니다.
// 이 파일은 계좌의 ID·소유 회사·잔액·생성 시각을 보관하고, 가능한 입출금인지 검사합니다.
// 읽는 순서: 보관할 값 → 0원 계좌를 만드는 생성자 → deposit(입금) → withdraw(출금).
//
// 메서드를 호출하면 우선 Java 객체의 잔액이 바뀝니다. 이것만으로 DB 저장이 끝나지는 않습니다.
// JPA가 트랜잭션 안에서 관리하는 객체라면 변경 내용을 확인해 DB에 반영할 수 있습니다.
// 이렇게 객체의 바뀐 값을 찾아 저장하는 기능을 "변경 감지"라고 부릅니다.
// 계좌 간 이체에서 출금·입금을 함께 성공시키고 다른 요청과 충돌하지 않게 하는 일은
// TransferWorkflowService가 담당합니다. 이 클래스 혼자서 전체 이체를 처리하지는 않습니다.
// 현재는 원 단위 모의 계좌이며, 실제 은행 계좌번호나 거래 원장은 없습니다.

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

// JPA가 이 객체와 DB 데이터를 연결하도록 하는 표시입니다.
// 계좌마다 따로 생기는 데이터 객체이며, 업무 담당 Service처럼 공유해 쓰는 빈과는 다릅니다.
@Entity
// 연결할 테이블 이름입니다. accounts의 한 행이 계좌 한 개에 해당합니다.
@Table(name = "accounts")
public class Account {

    // 계좌를 구별하는 ID입니다. 실제 은행 계좌번호가 아니라 앱 내부에서 쓰는 UUID입니다.
    // 새 계좌를 저장할 때 Hibernate가 ID를 만듭니다.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 여러 계좌(Many)가 한 회사(One)에 속할 수 있어 @ManyToOne입니다.
    // 회사 하나에 계좌가 3개라면, 계좌 3개가 모두 같은 회사 ID를 가리킵니다.
    // @JoinColumn은 그 ID를 accounts의 company_id 열에 넣어 연결하라는 뜻입니다.
    // optional=false와 nullable=false는 회사 없이 계좌를 만들 수 없다는 설정입니다.
    //
    // LAZY는 회사 상세 정보를 필요할 때 읽도록 하는 설정입니다. 이를 "지연 로딩"이라고 부릅니다.
    // 예를 들어 회사 이름을 읽는 순간 추가 DB 조회가 일어날 수 있습니다.
    // 필요한 연결을 아직 읽지 않았는데 JPA의 관리가 끝났다면 나중에 접근할 때 오류가 날 수 있습니다.
    // updatable=false는 JPA가 계좌 소유 회사를 수정하는 SQL을 만들지 않는다는 뜻이며 권한 검사는 아닙니다.
    // 계좌를 저장한다고 새 회사까지 자동 저장하도록 설정하지 않았으므로 기존 회사를 연결합니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    // 원 단위 잔액입니다. 1000L은 1,000원이고, 끝의 L은 long 정수라는 뜻입니다.
    // long은 큰 정수를 담지만 담을 수 있는 최대값이 있습니다. 입금할 때 그 범위도 검사합니다.
    // 지금은 0.5원 같은 소수 금액이나 외화는 다루지 않습니다.
    @Column(name = "balance_won", nullable = false)
    private long balanceWon;

    // 계좌 생성 시각은 DB의 기본값으로 채웁니다.
    // insertable=false와 updatable=false는 JPA가 이 열을 저장·수정 SQL에서 빼게 합니다.
    // 저장 직후 Java 객체에 시각이 바로 들어오지는 않습니다. DB에서 새로 읽거나 refresh해야 확인할 수 있습니다.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // JPA가 DB의 계좌 정보를 읽어 객체로 만들 때 사용하는 빈 생성자입니다.
    // 우리 업무 코드는 아래처럼 회사를 받는 생성자로 새 계좌를 만듭니다.
    protected Account() {
    }

    // new Account(회사)로 호출합니다. 회사가 없으면 생성을 중단합니다.
    // 초기 잔액은 입력받지 않고 항상 0원으로 시작합니다. this는 지금 만드는 계좌 객체입니다.
    public Account(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("Company must be provided");
        }
        this.company = company;
        this.balanceWon = 0L;
    }

    // 입금 순서: 0원·음수 거부 → 기존 잔액에 금액 더하기 → 계산 결과로 잔액 변경.
    // 예: 잔액 1,000원에 300원 입금 → 1,300원. void는 따로 돌려줄 값 없이 객체의 잔액을 바꾼다는 뜻입니다.
    // Math.addExact는 합계가 long의 최대 범위를 넘으면 예외를 보냅니다. 이를 오버플로 검사라고 합니다.
    // 오른쪽 계산이 성공해야 =로 대입하므로 계산 실패 시 이 객체의 기존 잔액은 그대로입니다.
    public void deposit(long amountWon) {
        requirePositiveAmount(amountWon);
        balanceWon = Math.addExact(balanceWon, amountWon);
    }

    // 출금 순서: 0원·음수 거부 → 잔액이 충분한지 확인 → 잔액에서 금액 빼기.
    // 예: 잔액 1,000원에서 300원 출금 → 700원. 1,200원 출금 요청 → 예외가 나고 1,000원 유지.
    // -=는 "오른쪽 금액만큼 빼서 다시 저장"입니다. 잔액 전부를 출금하면 0원이 됩니다.
    // 이 검사는 이 객체 하나의 규칙입니다. 여러 요청이 동시에 출금하는 문제는 서비스의 DB 잠금으로 다룹니다.
    public void withdraw(long amountWon) {
        requirePositiveAmount(amountWon);
        if (amountWon > balanceWon) {
            throw new InsufficientBalanceException();
        }
        balanceWon -= amountWon;
    }

    // 입금과 출금 양쪽에서 사용하는 공통 검사입니다. <=는 "작거나 같다"입니다.
    // 0원 이하만 거부하므로, 이 검사를 통과해도 잔액 부족이나 숫자 범위 초과 검사는 따로 필요합니다.
    // private은 이 클래스 안에서만 사용, static은 특정 계좌의 값에 기대지 않는 공통 기능이라는 뜻입니다.
    private static void requirePositiveAmount(long amountWon) {
        if (amountWon <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    // 아래 getter들은 이 계좌가 보관한 값을 읽는 통로입니다. 예: getBalanceWon()은 현재 잔액을 반환합니다.
    // 잔액을 아무 숫자로 덮어쓰는 setter는 두지 않았습니다. 입출금 메서드를 거치며 규칙을 검사하게 합니다.
    // getCompany()로 받은 회사의 상세 정보를 읽으면 앞에서 설명한 지연 로딩이 일어날 수 있습니다.
    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public long getBalanceWon() { return balanceWon; }
    public Instant getCreatedAt() { return createdAt; }
}
