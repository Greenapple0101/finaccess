-- 결재 이력을 요청 행에 기록합니다. 별도 전체 감사 이벤트 로그는 후속 범위입니다.
ALTER TABLE transfer_requests
    ADD COLUMN reviewer_id UUID REFERENCES app_users (id),
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_transfer_review_pair CHECK ((reviewer_id IS NULL) = (reviewed_at IS NULL)),
    ADD CONSTRAINT ck_transfer_no_self_review CHECK (reviewer_id IS NULL OR reviewer_id <> requester_id);
-- 기존 행의 과거 결재자를 추측해 채우지 않습니다. 신규 결재는 Java 메서드가 두 값을 함께 넣습니다.
CREATE INDEX idx_transfer_reviewer ON transfer_requests (reviewer_id);
