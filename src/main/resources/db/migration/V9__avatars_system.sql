-- V9: Avatar system (Tarea 14 / ONE-14)
--
-- Replaces the placeholder `user_avatars` table introduced in V5 (which was a
-- denormalized client-driven sync) with a full server-owned avatar system:
--   - avatars_catalog: server-owned catalog with an `is_initial_free` flag,
--                      independent of rarity, toggleable at runtime.
--   - user_avatars:    per-user inventory with acquisition source tracking
--                      (INITIAL_FREE today, PURCHASE in the future shop).
--   - user_avatar_state: equipped avatar + initial-free change counters and
--                      window timestamps, enforcing the "max 2 changes,
--                      first after 24h, second after 7d" rule.
--
-- The old `user_avatars` table only stored what the client uploaded via the
-- ONE-27 sync snapshot. Per ONE-14 spec, "no hay nada que migrar" — the
-- frontend never released this so we simply drop and rebuild.

DROP TABLE IF EXISTS user_avatars;

-- ── Catalog ───────────────────────────────────────────────────────────────
CREATE TABLE avatars_catalog (
    avatar_id        VARCHAR(64)  PRIMARY KEY,
    name             TEXT         NOT NULL,
    description      TEXT,
    rarity           VARCHAR(16)  NOT NULL,                  -- COMMON, RARE, EPIC, LEGENDARY, MYTHIC
    image_url        TEXT         NOT NULL,
    is_initial_free  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rarity CHECK (rarity IN ('COMMON','RARE','EPIC','LEGENDARY','MYTHIC'))
);
CREATE INDEX idx_avatars_catalog_initial_free
    ON avatars_catalog (is_initial_free) WHERE is_initial_free = TRUE;

-- ── User inventory ────────────────────────────────────────────────────────
-- One row per (user, avatar). FK to catalog so the avatar must exist.
-- `acquisition_source` distinguishes free onboarding picks from future shop
-- purchases — initial-free changes only delete entries where
-- acquisition_source = 'INITIAL_FREE'.
CREATE TABLE user_avatars (
    user_id              BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    avatar_id            VARCHAR(64)  NOT NULL REFERENCES avatars_catalog(avatar_id),
    acquisition_source   VARCHAR(16)  NOT NULL,
    acquired_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, avatar_id),
    CONSTRAINT chk_acq_source CHECK (acquisition_source IN ('INITIAL_FREE','PURCHASE'))
);
CREATE INDEX idx_user_avatars_user ON user_avatars (user_id);

-- ── User state (equipped + change counters) ───────────────────────────────
-- One row per user. Created lazily on first onboarding.
-- `equipped_avatar_id` is NULL while inventory is empty; once set, it must
-- always reference a row in user_avatars (enforced by service layer, not FK,
-- because FKs across composite PKs add complexity for little gain).
CREATE TABLE user_avatar_state (
    user_id                          BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    equipped_avatar_id               VARCHAR(64),
    initial_free_acquired_at         TIMESTAMPTZ,
    initial_free_changes_used        INTEGER      NOT NULL DEFAULT 0,
    last_initial_free_change_at      TIMESTAMPTZ,
    updated_at                       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_changes_used CHECK (initial_free_changes_used BETWEEN 0 AND 2)
);

-- ── Seed: 5 default initial-free avatars ─────────────────────────────────
-- These match the placeholder assets shipped in the current Flutter client
-- (`assets/images/avatar_1.png` ... `avatar_5.png`). Names and descriptions
-- are intentionally generic — they can be edited in-place from admin without
-- needing a new migration. The `is_initial_free` flag is what gates them as
-- onboarding options; toggling it in admin takes effect immediately.
INSERT INTO avatars_catalog (avatar_id, name, description, rarity, image_url, is_initial_free) VALUES
    ('cuber_001', 'Cuber Clásico',  'El compañero de todos los principios.',     'COMMON',     'assets/images/avatar_1.png', TRUE),
    ('cuber_002', 'Speedster',      'Manos rápidas, mente más rápida.',          'COMMON',     'assets/images/avatar_2.png', TRUE),
    ('cuber_003', 'Estratega',      'Resuelve antes de mover la primera capa.',  'RARE',       'assets/images/avatar_3.png', TRUE),
    ('cuber_004', 'Veterano',       'Mil scrambles forjaron la calma.',          'RARE',       'assets/images/avatar_4.png', TRUE),
    ('cuber_005', 'Maestro',        'Dedicación pura, dedos imparables.',        'EPIC',       'assets/images/avatar_5.png', TRUE);
