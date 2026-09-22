-- V1~V3를 고치지 않고 새 마이그레이션을 추가합니다. Flyway가 한 번 적용하고 기록합니다.
CREATE TABLE transfer_requests (
    -- 요청 자체의 기본키입니다. 앱의 Hibernate가 UUID를 생성합니다.
    id UUID PRIMARY KEY,
    -- REFERENCES는 존재하는 계좌·사용자만 연결하도록 하는 외래키입니다.
    -- 다른 회사 소속인지나 호출자의 권한까지 검사하지는 않습니다.
    source_account_id UUID NOT NULL REFERENCES accounts (id),
    destination_account_id UUID NOT NULL REFERENCES accounts (id),
    amount_won BIGINT NOT NULL,
    requester_id UUID NOT NULL REFERENCES app_users (id),
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Java 생성자를 거치지 않는 직접 SQL에도 기본 규칙을 적용합니다.
    CONSTRAINT ck_transfer_positive_amount CHECK (amount_won > 0),
    CONSTRAINT ck_transfer_different_accounts CHECK (source_account_id <> destination_account_id),
    -- 허용되는 상태 이름만 검사합니다. 이전 상태와 비교하는 전이 검사는 아닙니다.
    CONSTRAINT ck_transfer_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'COMPLETED'))
);

-- 계좌별·요청자별 조회 및 참조 행의 삭제/변경 검사에 활용할 인덱스입니다.
-- 인덱스는 접근 권한을 부여하거나 검사하는 기능이 아닙니다.
CREATE INDEX idx_transfer_source ON transfer_requests (source_account_id);
CREATE INDEX idx_transfer_destination ON transfer_requests (destination_account_id);
CREATE INDEX idx_transfer_requester ON transfer_requests (requester_id);
