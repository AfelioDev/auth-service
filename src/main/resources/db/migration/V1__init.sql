CREATE TABLE users (
    id            BIGSERIAL     PRIMARY KEY,
    name          VARCHAR(255)  NOT NULL,
    email         VARCHAR(255),
    password_hash VARCHAR(255),
    wca_id        VARCHAR(20),
    wca_access_token TEXT,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email  UNIQUE (email),
    CONSTRAINT uq_users_wca_id UNIQUE (wca_id),
    -- Every user must have at least one authentication method
    CONSTRAINT chk_users_auth  CHECK (password_hash IS NOT NULL OR wca_id IS NOT NULL)
);

CREATE INDEX idx_users_email  ON users (email);
CREATE INDEX idx_users_wca_id ON users (wca_id);
