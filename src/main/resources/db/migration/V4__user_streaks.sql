-- V4: Daily streak tracking (Duolingo-style, ONE-13)
-- One row per user. Days are tracked as DATE in the user's local timezone,
-- which is reported by the client at register-solve time and validated by
-- the server using its own NOW() — never trusting the device clock.

CREATE TABLE user_streaks (
    user_id          BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    current_streak   INTEGER      NOT NULL DEFAULT 0,
    longest_streak   INTEGER      NOT NULL DEFAULT 0,
    last_solve_date  DATE,                          -- in last_timezone
    last_timezone    VARCHAR(64),                   -- IANA (e.g. America/Mexico_City)
    started_at       TIMESTAMP,                     -- when current_streak began
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_streaks_last_solve_date ON user_streaks (last_solve_date);
