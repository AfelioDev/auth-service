-- V10: Account bans (Tarea 5 / ONE-9)
--
-- Adds three nullable columns to `users` so an admin can ban an account from
-- logging in or reaching authenticated endpoints. The presence of `banned_at`
-- alone marks the user as banned; `ban_until` distinguishes temporary bans
-- (login allowed again automatically when NOW() > ban_until) from permanent
-- ones (NULL = permanent).
--
-- Old rows have all three NULL → not banned, business as usual.

ALTER TABLE users
    ADD COLUMN banned_at  TIMESTAMPTZ,
    ADD COLUMN ban_reason TEXT,
    ADD COLUMN ban_until  TIMESTAMPTZ;

-- Partial index so the "is this user currently banned" lookup stays fast even
-- as the table grows. Most rows are unbanned (NULL banned_at), and the index
-- only covers banned ones.
CREATE INDEX idx_users_currently_banned
    ON users (id)
    WHERE banned_at IS NOT NULL;
