-- ----------------------------------------------------------------------------
-- Economía F-ECO-2 — inventario: qué ítems tiene cada personaje y cuántos. El catálogo de
-- ítems (id, precio, efecto, categoría, rama de trabajo) vive en código. Comprar descuenta
-- coins (atómico) y suma al inventario; usar un consumible aplica su efecto y descuenta 1.
-- ----------------------------------------------------------------------------
CREATE TABLE inventario (
    discord_id  BIGINT      NOT NULL COMMENT 'Dueño (snowflake)',
    item_id     VARCHAR(40) NOT NULL COMMENT 'Id del ítem (catálogo en código)',
    cantidad    INT         NOT NULL DEFAULT 0 COMMENT 'Unidades que posee',
    PRIMARY KEY (discord_id, item_id),
    CONSTRAINT fk_inventario_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
