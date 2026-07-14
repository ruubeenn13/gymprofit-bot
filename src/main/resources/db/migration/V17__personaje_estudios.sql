-- ----------------------------------------------------------------------------
-- Progresión (F-ECO-3b) — atributo de estudios.
--
-- Nuevo atributo del personaje que sube con /estudiar (gastando energía, como /entrenar). Los
-- estudios dan un bono porcentual al pago de /work (más formación, mejor sueldo). Es una vía de
-- progresión lenta y un sumidero de energía adicional.
-- ----------------------------------------------------------------------------
ALTER TABLE personajes
    ADD COLUMN estudios INT NOT NULL DEFAULT 0 COMMENT 'Nivel de estudios (bono al sueldo); sube con /estudiar';
