-- 회사가 소유한 모의 원화 계좌입니다. 실제 은행 계좌번호는 아직 다루지 않습니다.
CREATE TABLE accounts (
    -- 애플리케이션 내부 식별자입니다.
    id UUID PRIMARY KEY,
    -- 한 회사에 여러 계좌를 연결할 수 있지만 계좌의 소유 회사는 하나입니다.
    company_id UUID NOT NULL REFERENCES companies (id),
    -- 원 단위 정수입니다. 소수점 금액, 외화, 마이너스 통장은 이번 범위에 없습니다.
    balance_won BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Java 코드를 거치지 않고 SQL로 수정해도 음수 잔액은 거부합니다.
    CONSTRAINT ck_accounts_nonnegative_balance CHECK (balance_won >= 0)
);

-- 회사별 계좌 조회를 돕는 인덱스입니다. 접근 권한을 검사하는 기능은 아닙니다.
CREATE INDEX idx_accounts_company_id ON accounts (company_id);
