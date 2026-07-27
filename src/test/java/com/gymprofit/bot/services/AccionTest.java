package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prueba de los números puros de {@link Accion}: precio de participación (derivado del prestigio, con
 * suelo 1) y dividendo por accionista (fracción del pool sobre el pot, redondeo a la baja).
 */
class AccionTest {

    @Test
    @DisplayName("precio = prestigio / 100, con suelo 1")
    void precio() {
        assertEquals(150, Accion.precioParticipacion(15_000));
        assertEquals(1, Accion.precioParticipacion(0));
        assertEquals(1, Accion.precioParticipacion(99));
    }

    @Test
    @DisplayName("dividendo = floor(pot * participaciones / 100)")
    void dividendo() {
        assertEquals(250, Accion.dividendoDe(1_000, 25));
        assertEquals(0, Accion.dividendoDe(1_000, 0));
        assertEquals(1_000, Accion.dividendoDe(1_000, 100));
        assertEquals(9, Accion.dividendoDe(99, 10));
    }
}
