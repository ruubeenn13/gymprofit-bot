-- ----------------------------------------------------------------------------
-- Minería (COMBAT-5a) — estado de minería por jugador.
--
-- La minería es una actividad universal, independiente del trabajo: cualquiera con un pico en el
-- inventario puede /minar. Esta tabla lleva el progreso (nivel de minería, sube con el uso) y el
-- cooldown de la actividad. El pico usado y los minerales obtenidos viven en el inventario (ids del
-- catálogo services/Items); los tiers de pico/mineral están en código (services/Picos, Minerales).
-- La durabilidad del pico y /reparar llegan en COMBAT-5b.
-- ----------------------------------------------------------------------------
CREATE TABLE mineria (
    discord_id     BIGINT    NOT NULL COMMENT 'Jugador (FK a usuarios_discord)',
    nivel_mineria  INT       NOT NULL DEFAULT 0 COMMENT 'Nivel de minería (sube con el uso de /minar)',
    ultimo_minado  TIMESTAMP NULL COMMENT 'Última vez que usó /minar (cooldown)',
    PRIMARY KEY (discord_id),
    CONSTRAINT fk_mineria_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Progreso y cooldown de la minería (RPG de combate).';
