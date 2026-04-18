-- V8: Social login identities (Google + Apple).
-- A user can have multiple linked providers (google, apple, wca, email-password).
-- Unique index on (provider, subject) enforces that one external identity maps
-- to at most one local user.

CREATE TABLE user_identities (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider    VARCHAR(32)  NOT NULL,  -- 'google' | 'apple'
    subject     VARCHAR(255) NOT NULL,  -- provider-issued stable id ('sub' claim)
    email       VARCHAR(255),           -- email reported by provider (may be private relay for Apple)
    linked_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP
);

CREATE UNIQUE INDEX idx_user_identities_provider_subject
    ON user_identities (provider, subject);

CREATE INDEX idx_user_identities_user
    ON user_identities (user_id);

-- Drop the V1 check that required password_hash OR wca_id. With Google/Apple,
-- a user's only auth method may be a row in user_identities, so the invariant
-- is now enforced at the application layer.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_auth;
