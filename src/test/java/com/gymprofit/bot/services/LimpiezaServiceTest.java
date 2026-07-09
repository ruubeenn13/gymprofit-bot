package com.gymprofit.bot.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica que {@link LimpiezaService#normalizar} acota la cantidad al rango permitido. La purga
 * en sí depende de JDA y se cubre con smoke test manual.
 */
class LimpiezaServiceTest {

    @Test
    void normalizarAcotaElRango() {
        assertEquals(1, LimpiezaService.normalizar(0), "por debajo del mínimo -> 1");
        assertEquals(1, LimpiezaService.normalizar(-5));
        assertEquals(50, LimpiezaService.normalizar(50));
        assertEquals(1000, LimpiezaService.normalizar(5000), "por encima del máximo -> 1000");
        assertEquals(1000, LimpiezaService.normalizar(1000));
    }
}
