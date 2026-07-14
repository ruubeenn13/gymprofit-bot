-- ----------------------------------------------------------------------------
-- Mercado entre jugadores (F-ECO-4b) — anuncios de venta.
--
-- Cada anuncio pone a la venta una cantidad de un ítem a un precio por unidad. Los ítems se retiran
-- del inventario del vendedor al publicar (escrow: quedan "retenidos" en el anuncio) y se entregan al
-- comprador en la compra; si el vendedor retira el anuncio, se le devuelven. En la compra se aplica
-- una comisión (sumidero) sobre lo que recibe el vendedor. El catálogo de ítems vive en código.
-- ----------------------------------------------------------------------------
CREATE TABLE mercado (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Id del anuncio',
    vendedor   BIGINT      NOT NULL COMMENT 'Jugador que vende (FK a usuarios_discord)',
    item_id    VARCHAR(40) NOT NULL COMMENT 'Id del ítem (catálogo services/Items)',
    cantidad   INT         NOT NULL COMMENT 'Unidades a la venta (en escrow)',
    precio     BIGINT      NOT NULL COMMENT 'Precio por unidad en coins',
    creado_en  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_mercado_vendedor FOREIGN KEY (vendedor)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
) COMMENT 'Anuncios del mercado entre jugadores.';
