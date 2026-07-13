-- ----------------------------------------------------------------------------
-- Economía F-ECO-1 — trabajo y energía: se amplía `personajes` con el trabajo actual y la
-- marca de la última vez que trabajó (cooldown de /work). La energía se regenera con un job
-- periódico (EnergiaJob), no con timestamps por fila (más simple y robusto).
-- ----------------------------------------------------------------------------
ALTER TABLE personajes
    ADD COLUMN trabajo     VARCHAR(30) NULL COMMENT 'Id del trabajo actual (catálogo en código); NULL = en paro',
    ADD COLUMN ultimo_work TIMESTAMP   NULL COMMENT 'Última vez que usó /work (cooldown)';
