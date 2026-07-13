-- ----------------------------------------------------------------------------
-- Moderación: registro de auditoría de sanciones + ajuste de warns para texto cifrado.
--
-- `warns` (V1) sigue siendo la fuente del ESCALADO automático (cuenta avisos activos).
-- `sanciones` es el HISTORIAL COMPLETO de toda acción de moderación (warn/mute/timeout/
-- kick/ban/nick…), que alimenta `/modlogs` y sirve de prueba de accountability (RGPD).
--
-- El texto libre con posible dato personal (motivo, apodo previo) se guarda CIFRADO
-- (AES-GCM, ver util/Cifrador y ADR-009); por eso esas columnas son TEXT (el base64 del
-- cifrado no cabe en VARCHAR(500)) y warns.motivo se amplía a TEXT.
-- ----------------------------------------------------------------------------

-- Amplía warns.motivo para alojar el texto cifrado (antes VARCHAR(500) en claro).
ALTER TABLE warns MODIFY motivo TEXT NULL COMMENT 'Razón del warn (CIFRADA, AES-GCM)';

CREATE TABLE sanciones (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    guild_id      BIGINT       NOT NULL COMMENT 'ID del servidor (snowflake)',
    discord_id    BIGINT       NOT NULL COMMENT 'Usuario sancionado (snowflake)',
    moderador_id  BIGINT       NOT NULL COMMENT 'Staff que aplicó la sanción (snowflake)',
    tipo          VARCHAR(16)  NOT NULL COMMENT 'WARN, MUTE, UNMUTE, TIMEOUT, UNTIMEOUT, KICK, BAN, UNBAN, NICK',
    motivo        TEXT         NULL     COMMENT 'Razón (CIFRADA, AES-GCM)',
    nick_anterior TEXT         NULL     COMMENT 'Apodo previo, solo en NICK (CIFRADO, AES-GCM)',
    duracion_seg  BIGINT       NULL     COMMENT 'Duración en segundos (TIMEOUT/MUTE temporal); NULL si no aplica',
    creado_en     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Momento de la sanción',
    PRIMARY KEY (id),
    -- Historial por usuario ordenado por fecha (para /modlogs paginado).
    KEY idx_sanciones_usuario (guild_id, discord_id, creado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
