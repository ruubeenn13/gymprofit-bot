-- ----------------------------------------------------------------------------
-- Combate (COMBAT-2) — progreso de mundos por jugador.
--
-- Registra qué MUNDO ha completado cada jugador (jefe derrotado). El catálogo de mundos y
-- monstruos (nombre, nivel requerido, poder, loot…) vive en código (services/Mundos y
-- services/Monstruos); aquí solo persistimos el progreso: una fila por (jugador, mundo) con la
-- marca de si ya derrotó a su jefe. Un mundo se DESBLOQUEA al tener el jefe del mundo anterior
-- derrotado (la lógica de desbloqueo está en MundoService).
--
-- En COMBAT-2 aún no hay pelea, así que nadie marca jefe_derrotado todavía; la tabla queda lista
-- para que COMBAT-3 (batalla por turnos) la escriba al vencer a un jefe.
-- ----------------------------------------------------------------------------
CREATE TABLE progreso_mundos (
    discord_id      BIGINT      NOT NULL COMMENT 'Jugador (FK a usuarios_discord)',
    mundo           VARCHAR(40) NOT NULL COMMENT 'Id del mundo (catálogo services/Mundos)',
    jefe_derrotado  BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'TRUE = jefe del mundo vencido (mundo completado)',
    actualizado_en  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (discord_id, mundo),
    CONSTRAINT fk_progreso_mundos_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Progreso de mundos del RPG de combate: qué mundos ha completado cada jugador.';
