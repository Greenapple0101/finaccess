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
// 계좌 개설은 AccountService에서 이 객체를 생성해 저장합니다. 입출금 규칙은 아래 메서드로 제공하며 외부 API는 아직 없습니다.
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
    // 입출금 메서드에서만 잔액을 변경하며 검증 실패 시 기존 값을 유지합니다.
    public Account(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("Company must be provided");
        }
        this.company = company;
        this.balanceWon = 0L;
    }

    // [이번 단계 1] 입금 금액은 양수여야 합니다. 0원과 음수를 먼저 거부합니다.
    // 일반 + 연산은 long 범위를 넘으면 값이 잘못 돌아갈 수 있습니다(오버플로).
    // Math.addExact는 범위 초과 시 ArithmeticException을 던집니다.
    // 오른쪽 계산이 성공한 뒤에만 대입되므로 예외가 나면 원래 잔액이 유지됩니다.
    public void deposit(long amountWon) {
        requirePositiveAmount(amountWon);
        balanceWon = Math.addExact(balanceWon, amountWon);
    }

    // [이번 단계 2] 출금은 양수이면서 현재 잔액 이하인 경우에만 허용합니다.
    // 검사 후 차감하므로 실패한 요청이 잔액을 일부 변경하지 않습니다.
    // 0 <= balanceWon이고 0 < amountWon <= balanceWon이므로 뺄셈 결과는 음수가 되지 않습니다.
    public void withdraw(long amountWon) {
        requirePositiveAmount(amountWon);
        if (amountWon > balanceWon) {
            throw new InsufficientBalanceException();
        }
        balanceWon -= amountWon;
    }

    // 입금과 출금이 공유하는 입력 규칙입니다. private이라 외부에서 직접 호출하지 않습니다.
    private static void requirePositiveAmount(long amountWon) {
        if (amountWon <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    // 위 메서드는 객체 상태만 변경합니다. 새 객체라면 별도 저장이 필요하고,
    // 트랜잭션 안에서 조회한 관리 엔티티라면 JPA 변경 감지로 DB에 반영할 수 있습니다.
    // 동시 요청의 충돌, 이체 원자성, 거래 이력은 이 메서드만으로 해결되지 않습니다.

    // getter는 현재 값을 읽기만 합니다. 입출금이나 저장을 수행하는 메서드가 아닙니다.
    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public long getBalanceWon() { return balanceWon; }
    public Instant getCreatedAt() { return createdAt; }
}
