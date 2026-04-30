-- V14: Public friend code for spam-resistant friend requests (ONE-40).
--
-- The internal `users.id` is autoincrementing BIGINT (1, 2, 30, ...). Until
-- now that same numeric id was the public handle the client showed in
-- "Tu ID: #36" and accepted in `POST /social/friends/request`, which made
-- iterating ids trivial — a bot can spam every account in the system.
--
-- The fix is a non-predictable 8-digit code per user, exposed publicly,
-- generated with SecureRandom on registration. The internal id stays as
-- the foreign-key everywhere (no cascading migration across services /
-- WS payloads); the friend_code is the *only* public handle accepted to
-- create friendships.
--
-- 8 numeric digits = 100M combinations, plenty of room for the cuber base
-- for years. Easy to dictate, easy to type, easy to format as XXXX-XXXX
-- on the client.

ALTER TABLE users
    ADD COLUMN friend_code VARCHAR(8);

-- Backfill existing users with a unique random 8-digit code. Postgres'
-- random() is fine for this — the friend_code is not a secret, just a
-- non-predictable handle. Loop with a generated_series in case of
-- collisions on the unique index, retrying until each row gets a code.
DO $$
DECLARE
    u RECORD;
    candidate VARCHAR(8);
    tries INT;
BEGIN
    FOR u IN SELECT id FROM users WHERE friend_code IS NULL LOOP
        tries := 0;
        LOOP
            candidate := LPAD((floor(random() * 100000000))::int::text, 8, '0');
            BEGIN
                UPDATE users SET friend_code = candidate WHERE id = u.id;
                EXIT;
            EXCEPTION WHEN unique_violation THEN
                tries := tries + 1;
                IF tries > 20 THEN
                    RAISE EXCEPTION 'Could not assign friend_code for user %', u.id;
                END IF;
            END;
        END LOOP;
    END LOOP;
END$$;

ALTER TABLE users
    ALTER COLUMN friend_code SET NOT NULL,
    ADD CONSTRAINT uq_users_friend_code UNIQUE (friend_code),
    ADD CONSTRAINT chk_users_friend_code_format CHECK (friend_code ~ '^[0-9]{8}$');
