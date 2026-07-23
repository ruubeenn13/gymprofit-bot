-- ----------------------------------------------------------------------------
-- Gobernanza de empresas (F2) — gestión por votación de los altos cargos. Un Directivo (o el Dueño)
-- propone una acción sobre un miembro (cambiar su rango, sacarlo o despedirlo) y los altos cargos la
-- votan; la propuesta expira si nadie la resuelve a tiempo. Fase 1 dejó la estructura y la pertenencia;
-- aquí se añade la capa de decisiones colegiadas, sin tocar `empresa_miembros` (esa la mutan los
-- métodos del repositorio al aplicar una propuesta aprobada).
--
-- Charset explícito (como V27/V26/V25): `tipo` y `rango_nuevo` son identificadores de enum que se
-- cruzan en filtros; heredar el default del servidor (utf8mb4_0900_ai_ci en MySQL 8) invitaría a un
-- «illegal mix of collations».
-- ----------------------------------------------------------------------------

-- Propuestas: una fila por acción de gestión pendiente de voto. `objetivo_discord_id` es el miembro
-- afectado; `proponente_discord_id`, quien la abrió. `rango_nuevo` solo aplica a CAMBIAR_RANGO (NULL
-- en SACAR/DESPEDIR). El UNIQUE evita dos propuestas activas idénticas sobre el mismo miembro.
CREATE TABLE empresa_propuestas (
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Id de la propuesta',
    empresa_id             BIGINT       NOT NULL              COMMENT 'Empresa sobre la que se decide',
    tipo                   VARCHAR(16)  NOT NULL              COMMENT 'Acción propuesta (enum TipoPropuesta): CAMBIAR_RANGO | SACAR | DESPEDIR',
    objetivo_discord_id    BIGINT       NOT NULL              COMMENT 'Miembro afectado por la propuesta (snowflake)',
    rango_nuevo            VARCHAR(16)  NULL                  COMMENT 'Rango destino (enum RangoEmpresa); solo en CAMBIAR_RANGO, NULL en el resto',
    proponente_discord_id  BIGINT       NOT NULL              COMMENT 'Alto cargo que abrió la propuesta (snowflake)',
    creada                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Instante de creación',
    expira                 TIMESTAMP    NOT NULL              COMMENT 'Instante en que la propuesta caduca si no se resuelve',
    PRIMARY KEY (id),
    -- Una propuesta activa por (empresa, tipo, objetivo): no se acumulan votaciones duplicadas sobre
    -- el mismo miembro para la misma acción mientras la anterior sigue viva.
    UNIQUE KEY uq_propuesta_activa (empresa_id, tipo, objetivo_discord_id),
    -- CASCADE: al disolver la empresa desaparecen sus propuestas (y, por la cascada de votos, sus votos).
    CONSTRAINT fk_prop_empresa    FOREIGN KEY (empresa_id)             REFERENCES empresas (id)                 ON DELETE CASCADE,
    -- RGPD: al borrar al miembro afectado o al proponente (derecho de supresión) caen sus propuestas.
    CONSTRAINT fk_prop_objetivo   FOREIGN KEY (objetivo_discord_id)    REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE,
    CONSTRAINT fk_prop_proponente FOREIGN KEY (proponente_discord_id)  REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT 'Propuestas de gestión de empresa pendientes de voto (F2).';

-- Votos: un voto por (propuesta, votante). El BOOLEAN es el sentido del voto (true = Sí). La PK
-- compuesta garantiza que cada alto cargo vota una sola vez por propuesta.
CREATE TABLE empresa_votos (
    propuesta_id        BIGINT   NOT NULL COMMENT 'Propuesta que se vota',
    votante_discord_id  BIGINT   NOT NULL COMMENT 'Alto cargo que emite el voto (snowflake)',
    voto                BOOLEAN  NOT NULL COMMENT 'Sentido del voto: true = Sí (a favor), false = No',
    PRIMARY KEY (propuesta_id, votante_discord_id),
    -- CASCADE: al resolver/caducar la propuesta (se borra) desaparecen sus votos.
    CONSTRAINT fk_voto_propuesta FOREIGN KEY (propuesta_id)       REFERENCES empresa_propuestas (id)        ON DELETE CASCADE,
    -- RGPD: al borrar al votante (derecho de supresión) se limpian sus votos.
    CONSTRAINT fk_voto_votante   FOREIGN KEY (votante_discord_id) REFERENCES usuarios_discord (discord_id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT 'Votos de los altos cargos sobre una propuesta (F2).';
