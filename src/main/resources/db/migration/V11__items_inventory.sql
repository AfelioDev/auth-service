-- V11: Generic item inventory + streak protector (ONE-38)
--
-- Two layers:
--   - Catalog: server-owned definitions of every item that exists in the
--     system. The flag `enabled` turns acquisition on/off without losing
--     existing inventories. `consumption_mode` distinguishes AUTO (fired
--     by backend on a business condition — like the streak protector) from
--     MANUAL (fired by the user from the app). `max_stack` caps how many
--     copies of the item a single user can hold; reducing it later does
--     NOT trim existing inventories.
--   - Per-user inventory: one row per (user, item) only while quantity > 0
--     (a CHECK plus a delete-on-zero policy in the service layer).
--
-- Two append-only logs cover trazability:
--   - item_acquisitions: every gain (admin grant today, future shop / reward).
--   - item_consumptions: every spend (auto for streak protector today,
--     manual for future items). `seen_by_user_at` lets the client show a
--     "we used your protector for day X" notice once and mark it seen.
--
-- This migration ships with one item: streak_protector (max_stack = 2).

-- ── Catalog ─────────────────────────────────────────────────────────────
CREATE TABLE items_catalog (
    item_id           VARCHAR(64)  PRIMARY KEY,
    name              TEXT         NOT NULL,
    description       TEXT,
    icon_url          TEXT,
    consumption_mode  VARCHAR(16)  NOT NULL,           -- AUTO | MANUAL
    max_stack         INTEGER      NOT NULL,
    subsystem         VARCHAR(32)  NOT NULL,           -- STREAK | ...
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_consumption_mode CHECK (consumption_mode IN ('AUTO','MANUAL')),
    CONSTRAINT chk_max_stack_positive CHECK (max_stack > 0)
);

-- ── User inventory ──────────────────────────────────────────────────────
-- quantity > 0 invariant: when a consumption brings it to 0, the service
-- deletes the row instead of leaving a zeroed entry. This keeps the
-- "an item present in inventory means quantity > 0" rule SQL-checkable.
CREATE TABLE user_items (
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id     VARCHAR(64)  NOT NULL REFERENCES items_catalog(item_id),
    quantity    INTEGER      NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, item_id),
    CONSTRAINT chk_user_items_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX idx_user_items_user ON user_items (user_id);

-- ── Acquisitions log (trazabilidad) ─────────────────────────────────────
CREATE TABLE item_acquisitions (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id      VARCHAR(64)  NOT NULL,
    quantity     INTEGER      NOT NULL,
    source       VARCHAR(32)  NOT NULL,                -- ADMIN_GRANT | TEST_SEED | PURCHASE | ACHIEVEMENT
    source_ref   TEXT,                                  -- free-form context (e.g. order id)
    acquired_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_acq_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX idx_item_acquisitions_user ON item_acquisitions (user_id, acquired_at);

-- ── Consumptions log (trazabilidad + notificación al usuario) ───────────
CREATE TABLE item_consumptions (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id          VARCHAR(64)  NOT NULL,
    consumed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    day_covered      DATE,                              -- e.g. for streak protector: the missed day
    context_ref      TEXT,                              -- free-form (e.g. AUTO_STREAK_PROTECTOR)
    seen_by_user_at  TIMESTAMPTZ                        -- NULL = unseen; client marks it seen later
);
CREATE INDEX idx_item_consumptions_unseen
    ON item_consumptions (user_id) WHERE seen_by_user_at IS NULL;

-- ── Seed: streak_protector ──────────────────────────────────────────────
INSERT INTO items_catalog (item_id, name, description, icon_url,
                           consumption_mode, max_stack, subsystem, enabled)
VALUES (
    'streak_protector',
    'Protector de racha',
    'Evita que tu racha se rompa cuando no haces solves un día. Se consume automáticamente.',
    'assets/items/streak_protector.png',
    'AUTO',
    2,
    'STREAK',
    TRUE
);
