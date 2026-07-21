-- ----------------------------------------------------------------------------
-- Efectos pasivos — ranuras de equipo y bienes.
--
-- Los 20 ítems de EQUIPO y los 10 vehículos de BIEN pasan a dar bonos pasivos (sueldo, XP, energía,
-- minería y combate). El catálogo de bonos vive en código (services/Pasivos), igual que Picos, Camas
-- y Cofres: aquí solo se guarda QUÉ tiene equipado cada jugador en cada ranura.
--
-- Tabla aparte de `personajes` (mismo patrón que `mineria`, `descanso` y `durabilidad_picos`): el
-- record Personaje ya tiene 14 componentes y cada columna nueva obliga a tocar el record, el
-- repositorio y todos los constructores de los tests. Además esto es 1..N por jugador, no 1..1.
--
-- La fila es solo una REFERENCIA, no un derecho: PasivoService cruza siempre contra el inventario y
-- descarta las ranuras cuyo ítem ya no se posee. Por eso vender/regalar/publicar en el mercado no
-- necesita ningún hook de limpieza. El borrado RGPD lo cubre el ON DELETE CASCADE.
-- ----------------------------------------------------------------------------
CREATE TABLE pasivos_equipados (
    discord_id  BIGINT      NOT NULL COMMENT 'Jugador (FK a usuarios_discord)',
    ranura      TINYINT     NOT NULL COMMENT 'Ranura 1..4 (se desbloquean a nivel 0/10/25/50)',
    item_id     VARCHAR(40) NOT NULL COMMENT 'Id del ítem (catálogo services/Items; bonos en services/Pasivos)',
    equipado_en TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Cuándo se equipó (solo informativo)',
    -- PK (discord_id, ranura): una ranura solo puede tener un ítem. Reemplazar es DELETE + INSERT
    -- en una transacción (ver PasivoRepositorio.equipar: con ON DUPLICATE KEY UPDATE, el UNIQUE de
    -- abajo se tragaría el «mismo ítem en otra ranura» en vez de avisar).
    PRIMARY KEY (discord_id, ranura),
    -- La regla «un ítem no puede ocupar dos ranuras» queda garantizada EN EL ESQUEMA, no solo en el
    -- service: si dos comandos simultáneos corren la carrera, gana la base de datos.
    CONSTRAINT uq_pasivos_item UNIQUE (discord_id, item_id),
    CONSTRAINT fk_pasivos_equipados_usuario FOREIGN KEY (discord_id)
        REFERENCES usuarios_discord (discord_id) ON DELETE CASCADE
-- Charset explícito (como V1/V24): `item_id` se compara/cruza contra `inventario.item_id`, que es
-- utf8mb4_unicode_ci. Si esta tabla heredase el default del servidor (utf8mb4_0900_ai_ci en MySQL 8)
-- cualquier JOIN por item_id reventaría con «illegal mix of collations».
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT 'Ítems con efecto pasivo equipados por jugador, por ranura.';
