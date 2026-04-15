-- V3: App version requirements
-- Stores per-platform minimum and latest version strings so the Flutter app
-- can query them on startup and decide whether to show a blocking update
-- modal. Patches (1.2.0 -> 1.2.1) never block; only minor/major bumps do.
-- Messages are stored per-locale for direct rendering on the client.

CREATE TABLE version_requirements (
    platform      VARCHAR(16)  PRIMARY KEY,           -- 'ios' | 'android'
    min_version   VARCHAR(32)  NOT NULL,              -- lowest allowed, e.g. '1.3.0'
    latest_version VARCHAR(32) NOT NULL,              -- current store version
    store_url     VARCHAR(512) NOT NULL,              -- deep link to the store listing
    message_es    TEXT         NOT NULL,
    message_en    TEXT         NOT NULL,
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_platform CHECK (platform IN ('ios', 'android'))
);

-- Seed with the current app version (pubspec.yaml says 1.2.6+16).
-- minVersion starts equal to latestVersion so no users are force-updated
-- until the value is explicitly bumped.
INSERT INTO version_requirements (platform, min_version, latest_version, store_url, message_es, message_en)
VALUES
    ('ios', '1.2.6', '1.2.6',
     'https://apps.apple.com/app/one-timer/id0000000000',
     'Hay una versión nueva de One Timer disponible. Actualiza para continuar usando la app.',
     'A new version of One Timer is available. Please update to keep using the app.'),
    ('android', '1.2.6', '1.2.6',
     'https://play.google.com/store/apps/details?id=com.onetimer.app',
     'Hay una versión nueva de One Timer disponible. Actualiza para continuar usando la app.',
     'A new version of One Timer is available. Please update to keep using the app.');
