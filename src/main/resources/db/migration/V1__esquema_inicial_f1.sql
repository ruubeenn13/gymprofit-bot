-- ============================================================================
-- V1 · Esquema inicial de la BD del bot (gymprofit_bot) — Fase 1 (Núcleo).
--
-- Alcance F1 (SPEC §5 y §10): solo las tablas que usan los módulos de la Fase 1.
-- Las tablas de Economía (tienda_items, compras, insignias…), Competición (duelos,
-- retos…) llegan en migraciones posteriores (V-siguientes) cuando se abran sus fases.
--
-- Convenciones:
--   * Dominio en español (coherente con la app/API).
--   * IDs de Discord (usuario, guild, canal, rol, mensaje) son "snowflakes" de 64 bits:
--     caben en BIGINT con holgura durante décadas.
--   * utf8mb4 en todas las tablas para soportar emojis en textos visibles.
--   * Sellos de tiempo de auditoría (creado_en / actualizado_en) donde aportan.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- usuarios_discord: estado de gamificación por usuario (XP, nivel, economía, racha).
-- Se crea/actualiza (upsert) en la primera interacción del usuario con el bot.
-- ----------------------------------------------------------------------------
CREATE TABLE usuarios_discord (
    discord_id          BIGINT       NOT NULL COMMENT 'ID de usuario de Discord (snowflake)',
    xp                  INT          NOT NULL DEFAULT 0 COMMENT 'Puntos de experiencia acumulados',
    nivel               INT          NOT NULL DEFAULT 0 COMMENT 'Nivel derivado de la curva de XP',
    coins               INT          NOT NULL DEFAULT 0 COMMENT 'Moneda interna del bot',
    racha               INT          NOT NULL DEFAULT 0 COMMENT 'Días consecutivos de actividad',
    ultima_racha_fecha  DATE         NULL     COMMENT 'Último día que sumó racha (para calcular cortes)',
    idioma              VARCHAR(2)   NOT NULL DEFAULT 'es' COMMENT 'Override de idioma del usuario (es/en)',
    opt_out_logros      BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'Si el usuario NO quiere publicar sus logros',
    creado_en           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (discord_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- config_servidor: configuración por servidor (guild). Un registro por servidor.
-- Canales y roles se guardan como IDs; NULL = aún sin configurar por el staff.
-- ----------------------------------------------------------------------------
CREATE TABLE config_servidor (
    guild_id                    BIGINT      NOT NULL COMMENT 'ID del servidor de Discord (snowflake)',
    idioma                      VARCHAR(2)  NOT NULL DEFAULT 'es' COMMENT 'Idioma por defecto del servidor',
    canal_bienvenida            BIGINT      NULL COMMENT 'Canal donde se publica la bienvenida',
    canal_ejercicio_dia         BIGINT      NULL COMMENT 'Canal del ejercicio del día (job 8:00)',
    canal_logros                BIGINT      NULL COMMENT 'Canal donde se comparten logros',
    canal_sugerencias           BIGINT      NULL COMMENT 'Canal de sugerencias con votación',
    canal_soporte               BIGINT      NULL COMMENT 'Canal de soporte con el botón de abrir ticket',
    canal_bot_logs              BIGINT      NULL COMMENT 'Canal privado de staff para logs del bot',
    rol_objetivo_fuerza         BIGINT      NULL COMMENT 'Rol asignado al objetivo Fuerza',
    rol_objetivo_cardio         BIGINT      NULL COMMENT 'Rol asignado al objetivo Cardio',
    rol_objetivo_perdida_peso   BIGINT      NULL COMMENT 'Rol asignado al objetivo Pérdida de peso',
    rol_objetivo_general        BIGINT      NULL COMMENT 'Rol asignado al objetivo General',
    creado_en                   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en              TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (guild_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- warns: amonestaciones de moderación. El escalado (3 warns → timeout) se calcula
-- contando las activas. Se conserva el histórico (activo=FALSE al revocar).
-- ----------------------------------------------------------------------------
CREATE TABLE warns (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    discord_id      BIGINT       NOT NULL COMMENT 'Usuario amonestado',
    moderador_id    BIGINT       NOT NULL COMMENT 'Staff que puso el warn',
    motivo          VARCHAR(500) NULL     COMMENT 'Razón del warn',
    activo          BOOLEAN      NOT NULL DEFAULT TRUE COMMENT 'FALSE si fue revocado',
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_warns_usuario (discord_id, activo),
    CONSTRAINT fk_warns_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- tickets: hilos de soporte. Cada ticket abre un canal privado usuario+staff.
-- ----------------------------------------------------------------------------
CREATE TABLE tickets (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    discord_id      BIGINT       NOT NULL COMMENT 'Autor del ticket',
    canal_id        BIGINT       NULL     COMMENT 'Canal privado creado para el ticket',
    estado          VARCHAR(20)  NOT NULL DEFAULT 'ABIERTO' COMMENT 'ABIERTO | CERRADO',
    asunto          VARCHAR(200) NULL     COMMENT 'Asunto breve del ticket',
    transcript      MEDIUMTEXT   NULL     COMMENT 'Transcripción simple al cerrar',
    abierto_en      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cerrado_en      TIMESTAMP    NULL,
    PRIMARY KEY (id),
    KEY idx_tickets_estado (estado),
    KEY idx_tickets_usuario (discord_id),
    CONSTRAINT fk_tickets_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE,
    CONSTRAINT chk_tickets_estado CHECK (estado IN ('ABIERTO', 'CERRADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- sugerencias: propuestas de la comunidad con votación 👍/👎 y estados de staff.
-- ----------------------------------------------------------------------------
CREATE TABLE sugerencias (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    discord_id          BIGINT        NOT NULL COMMENT 'Autor de la sugerencia',
    mensaje_id          BIGINT        NULL     COMMENT 'Mensaje del embed con la votación',
    contenido           VARCHAR(1000) NOT NULL COMMENT 'Texto de la sugerencia',
    estado              VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE' COMMENT 'PENDIENTE | ACEPTADA | RECHAZADA',
    votos_positivos     INT           NOT NULL DEFAULT 0,
    votos_negativos     INT           NOT NULL DEFAULT 0,
    resuelto_por        BIGINT        NULL     COMMENT 'Staff que resolvió la sugerencia',
    creado_en           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resuelto_en         TIMESTAMP     NULL,
    PRIMARY KEY (id),
    KEY idx_sugerencias_estado (estado),
    CONSTRAINT fk_sugerencias_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE,
    CONSTRAINT chk_sugerencias_estado CHECK (estado IN ('PENDIENTE', 'ACEPTADA', 'RECHAZADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- trivia_preguntas: banco de preguntas bilingüe. Cada fila lleva ES y EN para
-- que la misma pregunta se muestre en el idioma del usuario (SPEC §8).
-- ----------------------------------------------------------------------------
CREATE TABLE trivia_preguntas (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    categoria       VARCHAR(30)  NOT NULL COMMENT 'FITNESS | NUTRICION',
    dificultad      VARCHAR(10)  NOT NULL DEFAULT 'MEDIA' COMMENT 'FACIL | MEDIA | DIFICIL',
    pregunta_es     VARCHAR(300) NOT NULL,
    pregunta_en     VARCHAR(300) NOT NULL,
    opcion_a_es     VARCHAR(150) NOT NULL,
    opcion_a_en     VARCHAR(150) NOT NULL,
    opcion_b_es     VARCHAR(150) NOT NULL,
    opcion_b_en     VARCHAR(150) NOT NULL,
    opcion_c_es     VARCHAR(150) NOT NULL,
    opcion_c_en     VARCHAR(150) NOT NULL,
    opcion_d_es     VARCHAR(150) NOT NULL,
    opcion_d_en     VARCHAR(150) NOT NULL,
    correcta        CHAR(1)      NOT NULL COMMENT 'Letra de la opción correcta: A | B | C | D',
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_trivia_categoria (categoria),
    CONSTRAINT chk_trivia_correcta CHECK (correcta IN ('A', 'B', 'C', 'D')),
    CONSTRAINT chk_trivia_categoria CHECK (categoria IN ('FITNESS', 'NUTRICION')),
    CONSTRAINT chk_trivia_dificultad CHECK (dificultad IN ('FACIL', 'MEDIA', 'DIFICIL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- trivia_scores: marcador acumulado de trivia por usuario.
-- ----------------------------------------------------------------------------
CREATE TABLE trivia_scores (
    discord_id      BIGINT      NOT NULL COMMENT 'Usuario',
    aciertos        INT         NOT NULL DEFAULT 0,
    fallos          INT         NOT NULL DEFAULT 0,
    partidas        INT         NOT NULL DEFAULT 0,
    mejor_racha     INT         NOT NULL DEFAULT 0 COMMENT 'Mejor racha de aciertos seguidos',
    actualizado_en  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (discord_id),
    CONSTRAINT fk_trivia_scores_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- frases: banco de frases motivadoras bilingüe (comando /frase y ejercicio del día).
-- ----------------------------------------------------------------------------
CREATE TABLE frases (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    texto_es    VARCHAR(400) NOT NULL,
    texto_en    VARCHAR(400) NOT NULL,
    autor       VARCHAR(120) NULL COMMENT 'Autor de la frase; NULL si es anónima/propia',
    categoria   VARCHAR(30)  NOT NULL DEFAULT 'MOTIVACION',
    creado_en   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
