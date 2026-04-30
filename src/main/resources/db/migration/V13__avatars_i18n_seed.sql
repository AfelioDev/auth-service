-- V13: Seed translations for the 5 starter avatars in the 10 locales the
-- app supports beyond Spanish (ONE-14 follow-up). The base 'es' was
-- seeded in V12 from `avatars_catalog.description`.
--
-- ISO 639-1 base codes; the locale parser in AvatarService normalizes
-- 'es-MX' / 'EN-US' / 'ZH-Hant' to 'es' / 'en' / 'zh', so the rows below
-- match exactly what an Accept-Language request resolves to. The one
-- exception is `zh-hant` (Chinese Traditional) which the parser keeps
-- as a distinct fallback below 'zh' — kept lowercased for join consistency.
--
-- Native review is recommended for ja, hi, zh, zh-hant and fil — the
-- copies below are best-effort. Edits go in subsequent migrations or
-- via direct INSERT ... ON CONFLICT DO UPDATE.

INSERT INTO avatars_catalog_i18n (avatar_id, locale, description) VALUES

-- ── English ─────────────────────────────────────────────────────────
('cuber_001', 'en', 'A trusty companion for your first solves.'),
('cuber_002', 'en', 'Quick hands, quicker mind.'),
('cuber_003', 'en', 'Solves before turning the first layer.'),
('cuber_004', 'en', 'A thousand scrambles forged the calm.'),
('cuber_005', 'en', 'Pure dedication, unstoppable fingers.'),

-- ── Français ────────────────────────────────────────────────────────
('cuber_001', 'fr', 'Le compagnon fidèle de tes premiers solves.'),
('cuber_002', 'fr', 'Mains rapides, esprit encore plus rapide.'),
('cuber_003', 'fr', 'Résout avant même de tourner la première couche.'),
('cuber_004', 'fr', 'Mille scrambles ont forgé ce calme.'),
('cuber_005', 'fr', 'Dévouement pur, doigts inarrêtables.'),

-- ── Italiano ────────────────────────────────────────────────────────
('cuber_001', 'it', 'Il compagno fidato dei tuoi primi solve.'),
('cuber_002', 'it', 'Mani veloci, mente ancora più veloce.'),
('cuber_003', 'it', 'Risolve prima di girare il primo strato.'),
('cuber_004', 'it', 'Mille scramble hanno forgiato la calma.'),
('cuber_005', 'it', 'Dedizione pura, dita inarrestabili.'),

-- ── Português (BR) ──────────────────────────────────────────────────
('cuber_001', 'pt', 'O companheiro de confiança dos seus primeiros solves.'),
('cuber_002', 'pt', 'Mãos rápidas, mente ainda mais rápida.'),
('cuber_003', 'pt', 'Resolve antes de virar a primeira camada.'),
('cuber_004', 'pt', 'Mil scrambles forjaram a calma.'),
('cuber_005', 'pt', 'Dedicação pura, dedos imparáveis.'),

-- ── Polski ──────────────────────────────────────────────────────────
('cuber_001', 'pl', 'Wierny towarzysz Twoich pierwszych ułożeń.'),
('cuber_002', 'pl', 'Szybkie ręce, jeszcze szybszy umysł.'),
('cuber_003', 'pl', 'Układa, zanim ruszy pierwszą warstwę.'),
('cuber_004', 'pl', 'Tysiąc scrambli wykuło ten spokój.'),
('cuber_005', 'pl', 'Czyste oddanie, niepowstrzymane palce.'),

-- ── 日本語 (best-effort, native review recommended) ────────────────
('cuber_001', 'ja', '最初の解法に寄り添う頼れる相棒。'),
('cuber_002', 'ja', '速い手、さらに速い頭。'),
('cuber_003', 'ja', '一段目を回す前にすでに解けている。'),
('cuber_004', 'ja', '千のスクランブルが磨いたこの冷静さ。'),
('cuber_005', 'ja', '純粋な情熱、止まらない指先。'),

-- ── हिन्दी (best-effort, native review recommended) ─────────────────
('cuber_001', 'hi', 'तुम्हारे पहले सॉल्व का भरोसेमंद साथी।'),
('cuber_002', 'hi', 'तेज़ हाथ, उससे भी तेज़ दिमाग़।'),
('cuber_003', 'hi', 'पहली परत घुमाने से पहले ही हल कर देता है।'),
('cuber_004', 'hi', 'हज़ार स्क्रैम्बल्स ने यह शांति गढ़ी।'),
('cuber_005', 'hi', 'शुद्ध समर्पण, अजेय उंगलियाँ।'),

-- ── 简体中文 (best-effort, native review recommended) ───────────────
('cuber_001', 'zh', '你每一次起步还原的可靠伙伴。'),
('cuber_002', 'zh', '手速快，脑子更快。'),
('cuber_003', 'zh', '在转动第一层之前就已经解开。'),
('cuber_004', 'zh', '千次打乱铸就的从容。'),
('cuber_005', 'zh', '纯粹的执着，停不下的指尖。'),

-- ── 繁體中文 (best-effort, native review recommended) ──────────────
('cuber_001', 'zh-hant', '你每一次起步還原的可靠夥伴。'),
('cuber_002', 'zh-hant', '手速快，腦子更快。'),
('cuber_003', 'zh-hant', '在轉動第一層之前就已經解開。'),
('cuber_004', 'zh-hant', '千次打亂鑄就的從容。'),
('cuber_005', 'zh-hant', '純粹的執著，停不下的指尖。'),

-- ── Filipino (best-effort, native review recommended) ──────────────
('cuber_001', 'fil', 'Tapat na kasama sa iyong unang mga solve.'),
('cuber_002', 'fil', 'Mabilis na kamay, mas mabilis na isip.'),
('cuber_003', 'fil', 'Nakakasolusyon na bago pa ikutin ang unang layer.'),
('cuber_004', 'fil', 'Libong scramble ang humubog ng kalmadong ito.'),
('cuber_005', 'fil', 'Dalisay na dedikasyon, walang humpay na daliri.')

ON CONFLICT (avatar_id, locale) DO UPDATE SET description = EXCLUDED.description;
