-- 실제 내부 이체 성공과 함께 기록할 완료 정보입니다.
ALTER TABLE transfer_requests
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN executed_by UUID REFERENCES app_users (id),
    ADD CONSTRAINT ck_transfer_execution_pair CHECK ((completed_at IS NULL) = (executed_by IS NULL));
CREATE INDEX idx_transfer_executor ON transfer_requests (executed_by);
