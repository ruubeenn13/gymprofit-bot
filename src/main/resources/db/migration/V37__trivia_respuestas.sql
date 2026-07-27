-- V37: respuestas de trivia (F4). Una fila por (jugador, pregunta): guarda si acertó y cuándo.
-- Un intento por pregunta (PK compuesta): imposible re-responder ni re-cobrar. FK CASCADE (RGPD).
CREATE TABLE trivia_respuestas (
    discord_id    BIGINT    NOT NULL,
    pregunta_id   BIGINT    NOT NULL,
    acierto       BOOLEAN   NOT NULL,
    respondida_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (discord_id, pregunta_id),
    CONSTRAINT fk_trivresp_usuario FOREIGN KEY (discord_id) REFERENCES usuarios_discord(discord_id) ON DELETE CASCADE,
    CONSTRAINT fk_trivresp_pregunta FOREIGN KEY (pregunta_id) REFERENCES trivia_preguntas(id) ON DELETE CASCADE,
    KEY idx_trivresp_usuario (discord_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
