CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    identity_issuer VARCHAR(512) NOT NULL CHECK (char_length(trim(identity_issuer)) > 0),
    identity_subject VARCHAR(255) NOT NULL CHECK (char_length(trim(identity_subject)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_users_identity UNIQUE (identity_issuer, identity_subject)
);

CREATE INDEX idx_app_users_company_id ON app_users (company_id);
