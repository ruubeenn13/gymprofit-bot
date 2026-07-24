package com.gymprofit.bot.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Prueba de las funciones puras de {@link Prestamo} (F5d): el límite de crédito escala con el nivel,
 * la deuda incorpora el interés fijo y la cuota reparte la deuda por el plazo redondeando hacia arriba.
 */
class PrestamoTest {

    @Test
    void limiteEsNivelPorFactor() {
        assertEquals(20_000, Prestamo.limite(1));
        assertEquals(200_000, Prestamo.limite(10));
    }

    @Test
    void deudaAplicaInteres() {
        assertEquals(24_000, Prestamo.deudaConInteres(20_000));
    }

    @Test
    void cuotaEsCeilPorPlazo() {
        assertEquals(6_000, Prestamo.cuota(24_000));
        assertEquals(2_500, Prestamo.cuota(10_000));
        assertEquals(3, Prestamo.cuota(9)); // ceil(9/4)=3
    }
}
