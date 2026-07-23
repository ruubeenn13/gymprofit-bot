-- ----------------------------------------------------------------------------
-- Empresas — entidad tipo gremio ligada a una rama de carrera. Fase 1: solo estructura y
-- pertenencia (fundar, invitar/solicitar, entrar/salir); la economía (bote, reparto, nivel)
-- llega en fases posteriores, pero las columnas `nivel` y `bote` se crean ya para no arrastrar
-- una migración de esquema cuando toque activarlas.
--
-- Charset explícito (como V26/V25/V24): `rama`, `nombre` y `rango` son identificadores de
-- catálogo o texto libre que se cruzan en JOINs; heredar el default del servidor
-- (utf8mb4_0900_ai_ci en MySQL 8) invitaría a un «illegal mix of collations».
-- ----------------------------------------------------------------------------

-- Empresa: una fila por empresa fundada. `rama` referencia el catálogo de ramas (services/Ascensos);
-- `dueno_discord_id` es el fundador. El nombre es único DENTRO de la rama (dos ramas pueden repetir).
CREATE TABLE empresas (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Id de la empresa',
    rama              VARCHAR(20)  NOT NULL              COMMENT 'Rama de carrera a la que pertenece (id de catálogo)',
    dueno_discord_id  BIGINT       NOT NULL              COMMENT 'Fundador/dueño (snowflake)',
    nombre            VARCHAR(64)  NOT NULL              COMMENT 'Nombre visible de la empresa',
    nivel             INT          NOT NULL DEFAULT 1    COMMENT 'Nivel de la empresa (economía de F2)',
    bote              BIGINT       NOT NULL DEFAULT 0    COMMENT 'Fondo común acumulado (economía de F2)',
    creada            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Instante de fundación',
    PRIMARY KEY (id),
    -- Nombre único por rama: evita dos empresas homónimas compitiendo en el mismo sector.
    UNIQUE KEY uq_empresa_nombre_rama (rama, nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT 'Empresas (gremios ligados a una rama de carrera).';

-- Miembros: pertenencia jugador↔empresa. Un jugador solo puede estar en UNA empresa a la vez
-- (uq_miembro_unico sobre discord_id), de ahí que no forme parte de la PK.
CREATE TABLE empresa_miembros (
    empresa_id  BIGINT       NOT NULL                       COMMENT 'Empresa a la que pertenece',
    discord_id  BIGINT       NOT NULL                       COMMENT 'Jugador miembro (snowflake)',
    rango       VARCHAR(16)  NOT NULL DEFAULT 'BECARIO'      COMMENT 'Rango interno (enum RangoEmpresa)',
    se_unio     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Instante de alta en la empresa',
    PRIMARY KEY (empresa_id, discord_id),
    -- Un jugador, una empresa: la pertenencia es exclusiva a nivel global.
    UNIQUE KEY uq_miembro_unico (discord_id),
    -- CASCADE: al borrar la empresa desaparecen sus membresías.
    CONSTRAINT fk_miembro_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT 'Pertenencia jugador↔empresa (exclusiva: una empresa por jugador).';

-- Pendientes: invitaciones (empresa→jugador) y solicitudes (jugador→empresa) sin resolver.
-- Un solo pendiente por par empresa/jugador (uq_pendiente_par) para no duplicar invitaciones.
CREATE TABLE empresa_pendientes (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Id del pendiente',
    empresa_id  BIGINT       NOT NULL              COMMENT 'Empresa implicada',
    discord_id  BIGINT       NOT NULL              COMMENT 'Jugador implicado (snowflake)',
    tipo        VARCHAR(12)  NOT NULL              COMMENT 'INVITACION (empresa→jugador) o SOLICITUD (jugador→empresa)',
    motivo      VARCHAR(300) NULL                  COMMENT 'Mensaje de la solicitud, o NULL en invitaciones',
    creada      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Instante de creación',
    PRIMARY KEY (id),
    -- Un pendiente por par: no se acumulan invitaciones/solicitudes repetidas al mismo jugador.
    UNIQUE KEY uq_pendiente_par (empresa_id, discord_id),
    -- CASCADE: al borrar la empresa se limpian sus pendientes.
    CONSTRAINT fk_pendiente_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT 'Invitaciones y solicitudes de pertenencia pendientes de resolver.';
