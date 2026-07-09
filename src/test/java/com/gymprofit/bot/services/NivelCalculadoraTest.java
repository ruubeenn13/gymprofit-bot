package com.gymprofit.bot.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica la curva de nivel (documentada en {@link NivelCalculadora}): umbrales y el nivel
 * resultante de una XP dada, incluidos los bordes.
 */
class NivelCalculadoraTest {

    @Test
    void umbralesDeXpPorNivel() {
        assertEquals(0, NivelCalculadora.xpParaNivel(0));
        assertEquals(100, NivelCalculadora.xpParaNivel(1));
        assertEquals(300, NivelCalculadora.xpParaNivel(2));
        assertEquals(600, NivelCalculadora.xpParaNivel(3));
        assertEquals(1000, NivelCalculadora.xpParaNivel(4));
    }

    @Test
    void nivelSegunXp() {
        assertEquals(0, NivelCalculadora.nivelDeXp(-10), "XP negativa = nivel 0");
        assertEquals(0, NivelCalculadora.nivelDeXp(0));
        assertEquals(0, NivelCalculadora.nivelDeXp(99));
        assertEquals(1, NivelCalculadora.nivelDeXp(100), "Justo en el umbral sube de nivel");
        assertEquals(1, NivelCalculadora.nivelDeXp(299));
        assertEquals(2, NivelCalculadora.nivelDeXp(300));
        assertEquals(3, NivelCalculadora.nivelDeXp(650));
    }
}
