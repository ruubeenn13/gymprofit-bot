-- ----------------------------------------------------------------------------
-- Combate (COMBAT-4c) — encantamientos del arma.
--
-- El encantamiento se aplica al arma equipada del jugador (no a instancias de ítem: el catálogo de
-- armas vive en código). Dos ejes:
--   * arma_nivel   : nivel de mejora del arma (+daño en combate); sube con /encantar, coste creciente.
--   * arma_encanto : id del efecto elemental aplicado (catálogo services/Encantamiento), NULL = ninguno.
-- Ambos modifican SOLO el combate (poder/daño/crítico/esquiva/robo de vida); se leen al iniciar la
-- pelea. No se persiste nada del combate en sí.
-- ----------------------------------------------------------------------------
ALTER TABLE personajes
    ADD COLUMN arma_nivel   INT         NOT NULL DEFAULT 0
        COMMENT 'Nivel de mejora del arma equipada (+daño en combate)',
    ADD COLUMN arma_encanto VARCHAR(40) NULL
        COMMENT 'Id del encantamiento del arma (catálogo Encantamiento), NULL = sin encantar';
