package com.gymprofit.bot.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica la fórmula de recompensa diaria (progresión lenta: escala poco y se topa). */
class EconomiaRepositorioTest {

    @Test
    void laRecompensaEscalaConLaRacha() {
        assertEquals(EconomiaRepositorio.DAILY_BASE + EconomiaRepositorio.DAILY_POR_RACHA,
                EconomiaRepositorio.recompensaDaily(1));
        assertTrue(EconomiaRepositorio.recompensaDaily(3) > EconomiaRepositorio.recompensaDaily(1),
                "más racha, más recompensa");
    }

    @Test
    void laRecompensaSeTopa() {
        int tope = EconomiaRepositorio.recompensaDaily(EconomiaRepositorio.DAILY_RACHA_TOPE);
        assertEquals(tope, EconomiaRepositorio.recompensaDaily(EconomiaRepositorio.DAILY_RACHA_TOPE + 50),
                "por encima del tope no sube más");
    }

    @Test
    void progresionLentaRecompensaModesta() {
        // Aun con racha altísima, el daily se mantiene modesto (grind intencionado).
        assertTrue(EconomiaRepositorio.recompensaDaily(1000) <= 100,
                "el daily no debe disparar la economía");
    }
}
