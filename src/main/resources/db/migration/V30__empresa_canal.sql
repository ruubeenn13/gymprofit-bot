-- V30: canal privado por empresa (F4 estatus). NULL hasta que se crea el canal
-- (creacion perezosa: la primera accion relevante lo materializa). BIGINT = snowflake de Discord.
ALTER TABLE empresas ADD COLUMN canal_id BIGINT NULL COMMENT 'Canal privado de la empresa (snowflake); NULL hasta crearlo (F4)';
