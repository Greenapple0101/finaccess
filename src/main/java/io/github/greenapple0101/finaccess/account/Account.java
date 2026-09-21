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

// [만든 순서 2] V3 SQL로 정의한 accounts 테이블에 Java 객체를 연결합니다.
// 이번에는 저장 모델만 만듭니다. 계좌 개설 API와 입출금 처리는 후속 단계입니다.
@Entity
@Table(name = "accounts")
public class Account {

    // UUID는 내부 식별자이며 실제 은행의 계좌번호가 아닙니다.
    // JPA가 영속화할 때 생성하므로 new Account(...) 직후에는 null일 수 있습니다.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 여러 계좌(Many)가 회사 하나(One)에 속합니다.
    // company_id 외래키에 회사 ID를 저장합니다. 회사 자체를 중복 저장하지 않습니다.
    // 현재 소유 회사 변경 기능은 없으므로 JPA UPDATE 대상에서 제외합니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    // 원화의 정수 금액만 표현합니다. 예: 1500L은 1,500원입니다.
    // long은 64비트 정수이며 DB의 BIGINT와 연결됩니다.
    // double/float의 근삿값 대신 정수를 사용합니다. 소수 단위·외화 지원은 별도 설계가 필요합니다.
    // 잔액은 0부터 Long.MAX_VALUE까지 표현할 수 있고, 실제 거래 한도는 이후 업무 규칙으로 정합니다.
    @Column(name = "balance_won", nullable = false)
    private long balanceWon;

    // 생성 시각은 DB 기본값이 채웁니다. 저장 후 재조회하면 값을 읽을 수 있습니다.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // JPA가 DB 조회 결과를 객체로 복원할 때 사용하는 기본 생성자입니다.
    protected Account() {
    }

    // 계좌를 새로 만들 때 초기 잔액을 외부에서 받지 않고 반드시 0원으로 시작합니다.
    // 잔액을 자유롭게 덮어쓰는 setter는 만들지 않습니다.
    // 향후 입출금 메서드에서 금액 검증·잔액 부족·정수 범위 초과를 명시적으로 처리합니다.
    public Account(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("Company must be provided");
        }
        this.company = company;
        this.balanceWon = 0L;
    }

    // getter는 현재 값을 읽기만 합니다. 입출금이나 저장을 수행하는 메서드가 아닙니다.
    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public long getBalanceWon() { return balanceWon; }
    public Instant getCreatedAt() { return createdAt; }
}
