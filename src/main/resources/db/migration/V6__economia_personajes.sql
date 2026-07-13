-- ----------------------------------------------------------------------------
-- Economía/RPG (Fase 2) — cimientos: personaje y ledger de transacciones.
--
-- El MONEDERO (coins) sigue en usuarios_discord.coins; aquí va el estado de RPG del
-- personaje (atributos, energía, salud). `transacciones` registra TODA variación de
-- coins (auditoría anti-trampa y depuración de balance). Columnas de trabajo/banco se
-- añadirán en migraciones posteriores según avancen las fases.
-- ----------------------------------------------------------------------------
CREATE TABLE personajes (
    discord_id   BIGINT   NOT NULL COMMENT 'Usuario (snowflake); 1:1 con usuarios_discord',
    fuerza       INT      NOT NULL DEFAULT 0   COMMENT 'Atributo Fuerza',
    resistencia  INT      NOT NULL DEFAULT 0   COMMENT 'Atributo Resistencia',
    carisma      INT      NOT NULL DEFAULT 0   COMMENT 'Atributo Carisma',
    energia      INT      NOT NULL DEFAULT 100 COMMENT 'Energía 0-100 (se gasta al trabajar/entrenar)',
    salud        INT      NOT NULL DEFAULT 100 COMMENT 'Salud 0-100',
    creado_en    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (discord_id),
    CONSTRAINT fk_personajes_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ledger de coins: cada fila = una variación (delta puede ser + o -).
CREATE TABLE transacciones (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    discord_id  BIGINT       NOT NULL COMMENT 'Usuario (snowflake)',
    delta       BIGINT       NOT NULL COMMENT 'Variación de coins (+ ingreso / - gasto)',
    motivo      VARCHAR(60)  NOT NULL COMMENT 'daily, work, tienda, regalo, gambling...',
    creado_en   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_transacciones_usuario (discord_id, creado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
