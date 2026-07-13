-- ----------------------------------------------------------------------------
-- Combate (COMBAT-1) — equipo del personaje: arma y armadura equipadas.
--
-- Guarda el id del ítem equipado en cada ranura (el catálogo de armas/armaduras, con su
-- ataque/defensa, vive en código: services/Items). NULL = ranura vacía (combate a puño / sin
-- armadura). El PODER DE COMBATE = fuerza + resistencia + arma.ataque + armadura.defensa se
-- calcula en el service; aquí solo persistimos qué hay equipado. El HP de combate llega en
-- COMBAT-3 (cuando haya pelea por turnos), no en esta migración.
-- ----------------------------------------------------------------------------
ALTER TABLE personajes
    ADD COLUMN arma     VARCHAR(40) NULL COMMENT 'Id del arma equipada (catálogo Items), NULL = a puño',
    ADD COLUMN armadura VARCHAR(40) NULL COMMENT 'Id de la armadura equipada (catálogo Items), NULL = sin armadura';
