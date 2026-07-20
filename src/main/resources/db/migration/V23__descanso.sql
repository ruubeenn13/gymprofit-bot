-- ----------------------------------------------------------------------------
-- Descanso (extra) — dormir es un estado, no un comando instantáneo.
--
-- La energía deja de subir sola: se recupera durmiendo. Al acostarse se sella el instante
-- (dormido_desde) y al despertar se gana energía proporcional al tiempo dormido de verdad y a la
-- cama que se tenga (catálogo en código, services/Camas), lo que por fin da un uso a los bienes de
-- vivienda. Tabla aparte de `personajes` (mismo patrón que `mineria`) para no engordar el record
-- Personaje con un estado que solo usa /descansar. El borrado RGPD lo cubre el ON DELETE CASCADE.
-- ----------------------------------------------------------------------------
CREATE TABLE descanso (
    discord_id       BIGINT      NOT NULL COMMENT 'Jugador (FK a usuarios_discord)',
    dormido_desde    TIMESTAMP   NULL COMMENT 'Instante en que se acostó; NULL = despierto',
    ultimo_despertar TIMESTAMP   NULL COMMENT 'Último despertar (para la fatiga: >24 h sin dormir)',
    consumidos_hoy   INT         NOT NULL DEFAULT 0 COMMENT 'Consumibles usados en dia_consumos (saciedad: máx. 3/día)',
    dia_consumos     DATE        NULL COMMENT 'Día natural (Europe/Madrid) al que pertenece consumidos_hoy',
    cama_pagada      VARCHAR(16) NULL COMMENT 'Cama pagada al acostarse (solo hotel); NULL = la mejor cama del inventario. El hotel no se posee: no saldría del inventario al despertar',
    PRIMARY KEY (discord_id),
    CONSTRAINT fk_descanso_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Estado de descanso por jugador (dormir, fatiga y saciedad).';
