-- ----------------------------------------------------------------------------
-- Economía F-ECO-2b — árbol de mejoras: qué nodos de mejora ha comprado cada personaje.
-- El catálogo del árbol (ramas, niveles, prerrequisitos, coste y bonus de atributo) vive en
-- código. Comprar un nodo descuenta coins, marca el nodo aquí y suma el bonus al atributo.
-- ----------------------------------------------------------------------------
CREATE TABLE mejoras (
    discord_id  BIGINT      NOT NULL COMMENT 'Dueño (snowflake)',
    nodo        VARCHAR(40) NOT NULL COMMENT 'Id del nodo del árbol de mejoras (catálogo en código)',
    creado_en   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (discord_id, nodo),
    CONSTRAINT fk_mejoras_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
