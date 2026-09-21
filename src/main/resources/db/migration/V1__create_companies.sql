CREATE TABLE companies (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (char_length(trim(name)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
