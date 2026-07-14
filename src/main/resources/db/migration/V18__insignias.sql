-- ----------------------------------------------------------------------------
-- Progresión (F-ECO-3b) — insignias ganadas.
--
-- Registra qué insignias (logros) ha desbloqueado cada jugador. El catálogo (condiciones) vive en
-- código (services/Insignias); las condiciones se evalúan sobre el estado actual del jugador al usar
-- /insignias y, si se cumplen por primera vez, se persisten aquí (permanentes aunque luego baje el
-- estado, p. ej. al gastar coins).
-- ----------------------------------------------------------------------------
CREATE TABLE insignias_ganadas (
    discord_id  BIGINT      NOT NULL COMMENT 'Jugador (FK a usuarios_discord)',
    insignia_id VARCHAR(40) NOT NULL COMMENT 'Id de la insignia (catálogo services/Insignias)',
    fecha       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (discord_id, insignia_id),
    CONSTRAINT fk_insignias_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Insignias (logros) desbloqueadas por jugador.';
