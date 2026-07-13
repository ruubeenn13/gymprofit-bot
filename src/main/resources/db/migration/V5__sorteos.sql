-- ----------------------------------------------------------------------------
-- sorteos: sorteos de la comunidad creados con /sorteo. El bot publica un embed en
-- 🎁・sorteos con reacción 🎉; quien reacciona participa. Un job cierra el sorteo al
-- llegar `fin_epoch`, elige `num_ganadores` al azar entre los participantes y lo anuncia.
--
-- El premio es texto PÚBLICO (no dato personal) → en claro. Los IDs son snowflakes.
-- ----------------------------------------------------------------------------
CREATE TABLE sorteos (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    guild_id       BIGINT        NOT NULL COMMENT 'Servidor (snowflake)',
    canal_id       BIGINT        NOT NULL COMMENT 'Canal donde se publicó el sorteo',
    mensaje_id     BIGINT        NOT NULL COMMENT 'Mensaje del sorteo (para leer sus reacciones)',
    premio         VARCHAR(200)  NOT NULL COMMENT 'Premio del sorteo (texto público)',
    num_ganadores  INT           NOT NULL DEFAULT 1 COMMENT 'Cuántos ganadores elegir',
    creador_id     BIGINT        NOT NULL COMMENT 'Staff que creó el sorteo (snowflake)',
    fin_epoch      BIGINT        NOT NULL COMMENT 'Instante de cierre en epoch (segundos)',
    activo         BOOLEAN       NOT NULL DEFAULT TRUE COMMENT 'FALSE cuando ya se resolvió',
    creado_en      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- El job busca sorteos activos ya vencidos por (activo, fin_epoch).
    KEY idx_sorteos_pendientes (activo, fin_epoch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
