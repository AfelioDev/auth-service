-- V7: FAQ section (ONE-6)
-- DB-backed so content can be edited without publishing a new app version.
-- Bilingual (es/en) with sort order and soft-delete via active flag.

CREATE TABLE faq_categories (
    id          SERIAL       PRIMARY KEY,
    name_es     VARCHAR(200) NOT NULL,
    name_en     VARCHAR(200) NOT NULL,
    icon        VARCHAR(50),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE faq_items (
    id            SERIAL       PRIMARY KEY,
    category_id   INTEGER      NOT NULL REFERENCES faq_categories(id) ON DELETE CASCADE,
    question_es   TEXT         NOT NULL,
    question_en   TEXT         NOT NULL,
    answer_es     TEXT         NOT NULL,
    answer_en     TEXT         NOT NULL,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_faq_items_category ON faq_items (category_id, sort_order);

-- ── Seed data ────────────────────────────────────────────────────────────

INSERT INTO faq_categories (name_es, name_en, icon, sort_order) VALUES
('Cuenta y perfil',          'Account & Profile',        'person',       1),
('Timer y scrambles',        'Timer & Scrambles',        'timer',        2),
('Smart cubes',              'Smart Cubes',              'bluetooth',    3),
('Rankings',                 'Rankings',                 'leaderboard',  4),
('Retos 1v1 y salas',       'Matches & Rooms',          'swords',       5),
('Torneos y competencias',   'Tournaments & Competitions','trophy',      6),
('Amigos y social',          'Friends & Social',         'people',       7),
('Privacidad y datos',       'Privacy & Data',           'shield',       8),
('Soporte y contacto',       'Support & Contact',        'help',         9);

-- Category 1: Cuenta y perfil
INSERT INTO faq_items (category_id, question_es, question_en, answer_es, answer_en, sort_order) VALUES
(1, '¿Cómo vinculo mi cuenta WCA a mi perfil?',
    'How do I link my WCA account to my profile?',
    'Ve a Configuración > Cuenta > Vincular WCA. Se abrirá la página de la WCA para autorizar el acceso. Una vez autorizado, tu perfil mostrará tu WCA ID, historial de competencias y rankings oficiales.',
    'Go to Settings > Account > Link WCA. The WCA website will open to authorize access. Once authorized, your profile will show your WCA ID, competition history, and official rankings.',
    1),
(1, '¿Puedo usar la app sin tener WCA ID?',
    'Can I use the app without a WCA ID?',
    'Sí. Puedes registrarte con email y contraseña y usar el timer, estadísticas, retos y todas las funcionalidades sociales. El WCA ID solo es necesario para ver tus rankings oficiales y tu historial de competencias.',
    'Yes. You can register with email and password and use the timer, statistics, matches, and all social features. A WCA ID is only needed to see your official rankings and competition history.',
    2),
(1, '¿Cómo cambio mi nombre de pantalla?',
    'How do I change my display name?',
    'Ve a Configuración > Nombre de pantalla. Puedes elegir una combinación de tu nombre real (al menos dos elementos de tu nombre WCA). Solo se puede cambiar una vez cada 30 días.',
    'Go to Settings > Display Name. You can choose a combination from your real name (at least two elements of your WCA name). It can only be changed once every 30 days.',
    3);

-- Category 2: Timer y scrambles
INSERT INTO faq_items (category_id, question_es, question_en, answer_es, answer_en, sort_order) VALUES
(2, '¿Los scrambles son oficiales WCA?',
    'Are the scrambles official WCA scrambles?',
    'Sí. One Timer usa TNoodle, el mismo generador de scrambles que utiliza la WCA en competencias oficiales.',
    'Yes. One Timer uses TNoodle, the same scramble generator used by the WCA in official competitions.',
    1),
(2, '¿Puedo usar el timer sin conexión a internet?',
    'Can I use the timer offline?',
    'Sí. El timer, los scrambles y tus solves locales funcionan sin conexión. Las funcionalidades online (retos, rankings en vivo, sincronización) requieren conexión.',
    'Yes. The timer, scrambles, and your local solves work offline. Online features (matches, live rankings, sync) require a connection.',
    2),
(2, '¿Cómo funciona el +2 y el DNF?',
    'How do +2 and DNF work?',
    '+2 añade 2 segundos a tu tiempo (penalización por desalineación al terminar). DNF (Did Not Finish) invalida el solve. Puedes marcar ambos después de registrar el tiempo tocando el solve en tu historial.',
    '+2 adds 2 seconds to your time (penalty for misalignment at the end). DNF (Did Not Finish) invalidates the solve. You can mark both after recording the time by tapping the solve in your history.',
    3),
(2, '¿Cómo se calcula el Ao5 y el Ao12?',
    'How are Ao5 and Ao12 calculated?',
    'El Average of 5 (Ao5) elimina el mejor y el peor tiempo de 5 solves y promedia los 3 restantes. El Ao12 hace lo mismo con 12 solves (elimina 1 mejor y 1 peor, promedia 10). Este es el mismo cálculo que usa la WCA.',
    'The Average of 5 (Ao5) drops the best and worst times from 5 solves and averages the remaining 3. Ao12 does the same with 12 solves (drops 1 best and 1 worst, averages 10). This is the same calculation used by the WCA.',
    4);

-- Category 3: Smart cubes
INSERT INTO faq_items (category_id, question_es, question_en, answer_es, answer_en, sort_order) VALUES
(3, '¿Qué smart cubes son compatibles?',
    'Which smart cubes are compatible?',
    'Actualmente One Timer es compatible con cubos QiYi Smart Cube. Estamos trabajando en soporte para más marcas.',
    'Currently One Timer is compatible with QiYi Smart Cube. We are working on support for more brands.',
    1),
(3, '¿Cómo conecto mi smart cube?',
    'How do I connect my smart cube?',
    'Ve a Configuración > Smart Cube > Conectar. Asegúrate de que el Bluetooth esté activado y que el cubo esté encendido. La app lo detectará automáticamente.',
    'Go to Settings > Smart Cube > Connect. Make sure Bluetooth is enabled and the cube is powered on. The app will detect it automatically.',
    2),
(3, '¿Qué pasa si mi smart cube se desconecta a mitad de un solve?',
    'What happens if my smart cube disconnects mid-solve?',
    'Si pierdes la conexión durante un solve, el tiempo seguirá corriendo. Puedes detenerlo manualmente. La app intentará reconectar automáticamente.',
    'If you lose the connection during a solve, the timer will keep running. You can stop it manually. The app will try to reconnect automatically.',
    3);

-- Category 4: Rankings
INSERT INTO faq_items (category_id, question_es, question_en, answer_es, answer_en, sort_order) VALUES
(4, '¿De dónde salen los rankings?',
    'Where do the rankings come from?',
    'Los rankings provienen de la base de datos oficial de la WCA, que incluye todos los resultados de competencias oficiales a nivel mundial. Se actualizan periódicamente.',
    'The rankings come from the official WCA database, which includes all results from official competitions worldwide. They are updated periodically.',
    1),
(4, '¿Cada cuánto se actualizan los rankings?',
    'How often are the rankings updated?',
    'Los datos se sincronizan automáticamente con la base de datos de la WCA. Normalmente los nuevos resultados aparecen dentro de unos días después de una competencia.',
    'The data is automatically synced with the WCA database. New results usually appear within a few days after a competition.',
    2);

-- Category 5: Retos y salas
INSERT INTO faq_items (category_id, question_es, question_en, answer_es, answer_en, sort_order) VALUES
(5, '¿Cómo invito a un amigo a un reto 1v1?',
    'How do I invite a friend to a 1v1 match?',
    'Ve a la sección de Amigos, selecciona a tu amigo y toca "Retar". Tu amigo recibirá una notificación y tendrá 60 segundos para aceptar.',
    'Go to the Friends section, select your friend, and tap "Challenge". Your friend will receive a notification and has 60 seconds to accept.',
    1),
(5, '¿Cómo funcionan las salas multiplayer?',
    'How do multiplayer rooms work?',
    'Crea una sala desde la sección Arena, configura las reglas (categoría, número de rondas, etc.) y comparte el código con tus amigos. Cuando estén todos, el host inicia la partida.',
    'Create a room from the Arena section, configure the rules (category, number of rounds, etc.), and share the code with your friends. When everyone is ready, the host starts the match.',
    2);

-- Category 6: Torneos
INSERT INTO faq_items (category_id, question_es, question_en, answer_es, answer_en, sort_order) VALUES
(6, '¿Las competencias listadas son oficiales WCA?',
    'Are the listed competitions official WCA events?',
    'Sí. Las competencias que aparecen en la sección de Competencias son eventos oficiales de la World Cube Association, obtenidos directamente de su base de datos.',
    'Yes. The competitions shown in the Competitions section are official World Cube Association events, sourced directly from their database.',
    1);

-- Category 7: Amigos y social
INSERT INTO faq_items (category_id, question_es, question_en, answer_es, answer_en, sort_order) VALUES
(7, '¿Cómo agrego amigos?',
    'How do I add friends?',
    'Busca al usuario por su nombre o WCA ID en la sección Social y envía una solicitud de amistad. El otro usuario debe aceptarla para que se agreguen mutuamente.',
    'Search for the user by name or WCA ID in the Social section and send a friend request. The other user must accept it for you to be added mutually.',
    1);

-- Category 8: Privacidad
INSERT INTO faq_items (category_id, question_es, question_en, answer_es, answer_en, sort_order) VALUES
(8, '¿Qué datos almacena la app sobre mí?',
    'What data does the app store about me?',
    'One Timer almacena tu perfil (nombre, email, WCA ID si lo vinculas), tus solves, sesiones, preferencias y lista de amigos. Puedes sincronizar esta información con la nube para no perderla al cambiar de dispositivo.',
    'One Timer stores your profile (name, email, WCA ID if linked), your solves, sessions, preferences, and friends list. You can sync this information to the cloud so you don''t lose it when switching devices.',
    1),
(8, '¿Puedo eliminar mi cuenta y mis datos?',
    'Can I delete my account and data?',
    'Sí. Contacta al equipo de soporte para solicitar la eliminación completa de tu cuenta y todos tus datos asociados.',
    'Yes. Contact the support team to request the complete deletion of your account and all associated data.',
    2);

-- Category 9: Soporte
INSERT INTO faq_items (category_id, question_es, question_en, answer_es, answer_en, sort_order) VALUES
(9, '¿Dónde reporto un bug o sugiero una mejora?',
    'Where do I report a bug or suggest an improvement?',
    'Puedes escribirnos directamente a nuestro correo de soporte o a través de nuestras redes sociales. ¡Valoramos mucho tu feedback!',
    'You can reach us directly at our support email or through our social media channels. We greatly value your feedback!',
    1);
