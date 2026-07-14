-- ----------------------------------------------------------------------------
-- Misiones de caza (COMBAT-6a) — progreso por jugador.
--
-- Cada misión es un objetivo de caza («mata N de X»); el catálogo (objetivo, meta, recompensa) vive
-- en código (services/Misiones). Aquí solo se guarda el progreso por (jugador, misión). Al ganar un
-- combate se incrementan las misiones que casan; cuando el progreso alcanza la meta, la misión se
-- completa automáticamente (paga coins + XP) y su progreso vuelve a 0 (misiones repetibles).
-- ----------------------------------------------------------------------------
CREATE TABLE mision_progreso (
    discord_id BIGINT      NOT NULL COMMENT 'Jugador (FK a usuarios_discord)',
    mision_id  VARCHAR(40) NOT NULL COMMENT 'Id de la misión (catálogo services/Misiones)',
    progreso   INT         NOT NULL DEFAULT 0 COMMENT 'Muertes acumuladas hacia la meta',
    PRIMARY KEY (discord_id, mision_id),
    CONSTRAINT fk_mision_progreso_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Progreso de las misiones de caza por jugador.';
