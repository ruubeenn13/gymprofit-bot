-- V39: marcador acumulado de trivia (F4). Da uso a trivia_scores (creada vacía en V1).
-- Añade racha_actual (racha viva) y rellena el marcador desde las respuestas ya jugadas.

-- Racha viva en curso; se rompe (0) al fallar. Permite el UPSERT O(1) sin recalcular.
ALTER TABLE trivia_scores ADD COLUMN racha_actual INT NOT NULL DEFAULT 0
    COMMENT 'Racha de aciertos seguidos en curso; 0 al fallar'
    AFTER mejor_racha;

-- Backfill idempotente desde trivia_respuestas. aciertos/fallos/partidas por agregación directa;
-- mejor_racha por gaps-and-islands (longitud máxima de aciertos consecutivos por respondida_en).
-- racha_actual se deja en 0 (la racha histórica viva no se reconstruye; se rehace jugando).
INSERT INTO trivia_scores (discord_id, aciertos, fallos, partidas, mejor_racha, racha_actual)
SELECT r.discord_id,
       SUM(r.acierto)        AS aciertos,
       SUM(1 - r.acierto)    AS fallos,
       COUNT(*)              AS partidas,
       COALESCE(mr.mejor, 0) AS mejor_racha,
       0                     AS racha_actual
FROM trivia_respuestas r
LEFT JOIN (
    SELECT discord_id, MAX(len) AS mejor
    FROM (
        SELECT discord_id, COUNT(*) AS len
        FROM (
            SELECT discord_id,
                   ROW_NUMBER() OVER (PARTITION BY discord_id ORDER BY respondida_en, pregunta_id)
                 - ROW_NUMBER() OVER (PARTITION BY discord_id, acierto ORDER BY respondida_en, pregunta_id) AS grp
            FROM trivia_respuestas
            WHERE acierto = TRUE
        ) x
        GROUP BY discord_id, grp
    ) y
    GROUP BY discord_id
) mr ON mr.discord_id = r.discord_id
GROUP BY r.discord_id
ON DUPLICATE KEY UPDATE
    aciertos    = VALUES(aciertos),
    fallos      = VALUES(fallos),
    partidas    = VALUES(partidas),
    mejor_racha = VALUES(mejor_racha);
