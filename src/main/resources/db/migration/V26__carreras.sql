-- ----------------------------------------------------------------------------
-- Ascensos de carrera — tier alcanzado por rama y antigüedad en el puesto.
--
-- `carreras` guarda el tier alcanzado POR RAMA (el catálogo de ramas/tiers vive en código,
-- services/Ascensos, igual que Trabajos, Picos o Pasivos): la fila es una REFERENCIA, no un
-- derecho — la elegibilidad se valida siempre contra el catálogo en tiempo de ejecución.
-- Sin fila = el usuario está en el tier de entrada de esa rama.
-- ----------------------------------------------------------------------------
CREATE TABLE carreras (
    discord_id     BIGINT      NOT NULL COMMENT 'Jugador (FK a usuarios_discord)',
    rama           VARCHAR(32) NOT NULL COMMENT 'Rama de carrera (id del catálogo services/Ascensos)',
    tier_alcanzado TINYINT     NOT NULL COMMENT 'Mayor tier desbloqueado en la rama (los inferiores quedan implícitos)',
    -- PK compuesta: un solo registro de progreso por jugador y rama.
    PRIMARY KEY (discord_id, rama),
    -- CASCADE: /privacidad borrar se lleva también las carreras (RGPD, como pasivos_equipados).
    CONSTRAINT fk_carreras_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
-- Charset explícito (como V1/V24/V25): `rama` es un id de catálogo que podría cruzarse contra
-- otras columnas utf8mb4_unicode_ci; heredar el default del servidor (utf8mb4_0900_ai_ci en
-- MySQL 8) invitaría a un «illegal mix of collations» en el primer JOIN.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT 'Tier de carrera alcanzado por jugador y rama (sin fila = tier de entrada).';

-- Antigüedad en el puesto actual: suma 1 por turno currado, se resetea al cambiar de puesto.
-- Columna en `personajes` (y no tabla aparte) porque es 1..1 con el trabajo actual del jugador.
ALTER TABLE personajes
    ADD COLUMN turnos_puesto INT NOT NULL DEFAULT 0
        COMMENT 'Turnos currados en el puesto actual; se resetea al cambiar de puesto';

-- Borrón y cuenta nueva acordado: no hay jugadores reales aún y el modelo de acceso al tier
-- cambia; nadie hereda un puesto que ya no podría elegir.
UPDATE personajes SET trabajo = NULL, turnos_puesto = 0;
