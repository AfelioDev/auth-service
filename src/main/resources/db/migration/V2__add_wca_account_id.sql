-- WCA returns a numeric 'id' (always present) in addition to 'wca_id' (only for competitors).
-- Store it as wca_account_id so WCA members who haven't competed can also log in.
ALTER TABLE users ADD COLUMN wca_account_id BIGINT;
ALTER TABLE users ADD CONSTRAINT uq_users_wca_account_id UNIQUE (wca_account_id);
CREATE INDEX idx_users_wca_account_id ON users (wca_account_id);

-- Relax the auth constraint: wca_account_id (not wca_id) is the WCA login identifier
ALTER TABLE users DROP CONSTRAINT chk_users_auth;
ALTER TABLE users ADD CONSTRAINT chk_users_auth
    CHECK (password_hash IS NOT NULL OR wca_account_id IS NOT NULL);
