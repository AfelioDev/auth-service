-- V6: Persistent sessions with refresh tokens (ONE-28)
-- Implements OAuth 2.1 token rotation with reuse detection.

-- Column on users to invalidate all access tokens at once (e.g. on password change)
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE refresh_tokens (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    VARCHAR(64)  NOT NULL UNIQUE,
    family_id     UUID         NOT NULL,
    device_id     VARCHAR(128),
    device_name   VARCHAR(128),
    ip            VARCHAR(45),
    issued_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMP    NOT NULL,
    last_used_at  TIMESTAMP,
    revoked_at    TIMESTAMP,
    replaced_by   UUID
);

CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_tokens_family
    ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_hash
    ON refresh_tokens (token_hash);
