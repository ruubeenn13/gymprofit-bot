-- ----------------------------------------------------------------------------
-- eventos_servidor: reto de la semana y próximo evento por servidor. Alimentan los
-- contadores "🎯 Reto" y "⏳ Evento" de la categoría SERVER STATS (job de estadísticas).
-- Un registro por servidor; NULL = aún sin fijar por el staff (/reto, /evento).
-- Tabla aparte de config_servidor para no engordar su registro de canales/roles.
-- ----------------------------------------------------------------------------
CREATE TABLE eventos_servidor (
    guild_id        BIGINT       NOT NULL COMMENT 'ID del servidor de Discord (snowflake)',
    reto_texto      VARCHAR(80)  NULL COMMENT 'Reto de la semana (texto corto que fija el staff)',
    evento_nombre   VARCHAR(60)  NULL COMMENT 'Nombre del próximo evento',
    evento_fin      BIGINT       NULL COMMENT 'Instante del evento en epoch (segundos) para la cuenta atrás',
    creado_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (guild_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
