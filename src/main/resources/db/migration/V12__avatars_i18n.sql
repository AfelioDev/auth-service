-- V12: i18n for the avatar catalog (ONE-14, follow-up).
--
-- The avatar catalog is server-owned and grows independently of client
-- releases (tienda, drops, packs). Translating the description copy via
-- client-side .arb files would defeat that — the client would need a
-- release whenever a new avatar lands.
--
-- The chosen pattern (per the frontend comment on ONE-14): the backend
-- resolves the locale from the Accept-Language header and returns the
-- description already translated. The avatar name is not translated — it
-- is identity of marca, same as Pikachu / Spotify.
--
-- Schema: a side table keyed by (avatar_id, locale). The base description
-- in `avatars_catalog.description` stays as the fallback when a request
-- locale has no translation. We seed the existing copies under locale 'es'
-- so explicit `Accept-Language: es` requests hit the side table directly.

CREATE TABLE avatars_catalog_i18n (
    avatar_id    VARCHAR(64)  NOT NULL REFERENCES avatars_catalog(avatar_id) ON DELETE CASCADE,
    locale       VARCHAR(8)   NOT NULL,                -- ISO 639-1 base ('es', 'en', 'fr', 'ja', ...)
    description  TEXT         NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (avatar_id, locale)
);

-- Seed the existing Spanish copies so a request with Accept-Language: es
-- uses the side-table value (and a future copy edit can target the i18n
-- row without touching the base description).
INSERT INTO avatars_catalog_i18n (avatar_id, locale, description)
SELECT avatar_id, 'es', description
  FROM avatars_catalog
 WHERE description IS NOT NULL;
