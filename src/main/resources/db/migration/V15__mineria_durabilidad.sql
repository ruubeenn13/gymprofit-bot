-- ----------------------------------------------------------------------------
-- Minería (COMBAT-5b) — durabilidad de los picos.
--
-- Cada pico que posee un jugador tiene durabilidad, que baja al minar. Al llegar a 0 el pico está
-- roto y no se puede usar hasta /reparar (sumidero de coins). La durabilidad máxima por pico vive en
-- código (services/Picos). Se guarda por (jugador, pico): si no hay fila, el pico está a tope.
-- Solo picos (las armas no se desgastan, para no complicar el combate).
-- ----------------------------------------------------------------------------
CREATE TABLE durabilidad_picos (
    discord_id  BIGINT      NOT NULL COMMENT 'Jugador (FK a usuarios_discord)',
    pico_id     VARCHAR(40) NOT NULL COMMENT 'Id del pico (catálogo services/Items, categoría PICO)',
    durabilidad INT         NOT NULL COMMENT 'Durabilidad actual (0 = roto)',
    PRIMARY KEY (discord_id, pico_id),
    CONSTRAINT fk_durabilidad_picos_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Durabilidad de los picos por jugador (minería).';
