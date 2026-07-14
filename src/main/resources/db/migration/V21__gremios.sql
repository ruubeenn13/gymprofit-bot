-- ----------------------------------------------------------------------------
-- Gremios (F-ECO-5a) — grupos de jugadores con canal privado.
--
-- Un gremio agrupa jugadores y tiene un canal de texto privado (visible solo para sus miembros por
-- permisos de MIEMBRO, sin rol, para no gastar el cupo de roles del servidor). Un jugador pertenece a
-- lo sumo a un gremio (PK por discord_id en la tabla de miembros). El id del canal se guarda para
-- gestionar permisos al entrar/salir y borrarlo al disolver.
-- ----------------------------------------------------------------------------
CREATE TABLE gremios (
    id        BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Id del gremio',
    nombre    VARCHAR(64) NOT NULL COMMENT 'Nombre del gremio (único)',
    dueno     BIGINT      NOT NULL COMMENT 'Jugador dueño/fundador (FK a usuarios_discord)',
    canal_id  BIGINT      NULL COMMENT 'Id del canal privado del gremio (Discord)',
    creado_en TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gremios_nombre (nombre),
    CONSTRAINT fk_gremios_dueno FOREIGN KEY (dueno)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Gremios (grupos de jugadores con canal privado).';

CREATE TABLE gremio_miembros (
    discord_id BIGINT NOT NULL COMMENT 'Jugador miembro (a lo sumo un gremio: PK)',
    gremio_id  BIGINT NOT NULL COMMENT 'Gremio al que pertenece',
    PRIMARY KEY (discord_id),
    CONSTRAINT fk_gm_gremio FOREIGN KEY (gremio_id) REFERENCES gremios (id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Pertenencia de jugadores a gremios.';
