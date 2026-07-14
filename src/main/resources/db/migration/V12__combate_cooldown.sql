-- ----------------------------------------------------------------------------
-- Combate (COMBAT-3) — cooldown de pelea.
--
-- Al PERDER una batalla se fija ultimo_combate = NOW(): el jugador entra en un cooldown corto
-- (ver COOLDOWN en BatallaService) antes de poder volver a pelear, además de perder salud. Ganar
-- no fija cooldown (la energía es el freno principal del ritmo). El HP de combate no se persiste:
-- se calcula al empezar la pelea (HP = base + resistencia·k) y vive en la sesión en memoria.
-- ----------------------------------------------------------------------------
ALTER TABLE personajes
    ADD COLUMN ultimo_combate TIMESTAMP NULL
        COMMENT 'Última derrota en combate (cooldown), NULL = sin cooldown activo';
