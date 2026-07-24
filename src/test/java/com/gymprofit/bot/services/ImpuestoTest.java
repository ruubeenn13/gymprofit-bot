package com.gymprofit.bot.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verifica los numeros del impuesto de empresa (F5b): la cuota semanal escala con el nivel y el
 * umbral de morosidad que provoca la quiebra es 3.
 */
class ImpuestoTest {

    @Test
    void cuotaEsNivelPorFactor() {
        assertEquals(2_500, Impuesto.cuota(1));
        assertEquals(25_000, Impuesto.cuota(10));
    }

    @Test
    void morosidadMaxEsTres() {
        assertEquals(3, Impuesto.MOROSIDAD_MAX);
    }
}
