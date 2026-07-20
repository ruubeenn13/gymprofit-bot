-- ----------------------------------------------------------------------------
-- Ejercicio del día (F1, consultas a la API) — histórico de publicaciones diarias.
--
-- Cada día natural (Europe/Madrid) se sortea un ejercicio del catálogo de la API entre los que
-- aún no han salido en la ronda actual; al agotar el catálogo (873 hoy) empieza la ronda
-- siguiente, de modo que nada se repite hasta haberlo visto todo. `fecha` como PK da la
-- idempotencia: si el job corre dos veces (reinicio, deploy) el segundo intento choca con la
-- fila y no vuelve a publicar. El id es de la API (no hay FK: el catálogo vive fuera del bot).
-- ----------------------------------------------------------------------------
CREATE TABLE ejercicio_dia (
    fecha        DATE      NOT NULL COMMENT 'Día natural (Europe/Madrid)',
    ejercicio_id INT       NOT NULL COMMENT 'Id del ejercicio en la API GymProFit',
    ronda        INT       NOT NULL DEFAULT 1 COMMENT 'Vuelta al catálogo (empieza en 1)',
    publicado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Cuándo se eligió/publicó',
    PRIMARY KEY (fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
