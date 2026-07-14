package com.gymprofit.bot.services;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el pago por turno y la coherencia del catálogo de trabajos. */
class TrabajoServiceTest {

    @Test
    void elPagoCaeDentroDelRango() {
        Random rng = new Random(7);
        for (int i = 0; i < 100; i++) {
            int pago = TrabajoService.calcularPago(80, 140, rng);
            assertTrue(pago >= 80 && pago <= 140, "pago fuera de rango: " + pago);
        }
    }

    @Test
    void losEstudiosDanBonoAlSueldoConTope() {
        assertEquals(100, TrabajoService.conBonoEstudios(100, 0));    // sin estudios
        assertEquals(110, TrabajoService.conBonoEstudios(100, 10));   // +10%
        assertEquals(125, TrabajoService.conBonoEstudios(100, 25));   // +25% (tope)
        assertEquals(125, TrabajoService.conBonoEstudios(100, 100));  // no pasa del tope
    }

    @Test
    void catalogoAmplioYConsistente() {
        assertTrue(Trabajos.CATALOGO.size() >= 25, "catálogo amplio");
        for (Trabajos t : Trabajos.CATALOGO) {
            assertTrue(t.salarioMin() > 0 && t.salarioMax() >= t.salarioMin(),
                    "rango de salario válido en " + t.id());
            assertTrue(t.energiaCoste() > 0 && t.energiaCoste() <= 100,
                    "coste de energía válido en " + t.id());
            assertTrue(Trabajos.porId(t.id()).isPresent(), "porId encuentra " + t.id());
        }
    }

    @Test
    void trabajosDeTierAltoPaganMasQueLosDeEntrada() {
        int maxTier1 = Trabajos.CATALOGO.stream().filter(t -> t.tier() == 1)
                .mapToInt(Trabajos::salarioMax).max().orElse(0);
        int minTier4 = Trabajos.CATALOGO.stream().filter(t -> t.tier() == 4)
                .mapToInt(Trabajos::salarioMin).min().orElse(0);
        assertTrue(minTier4 > maxTier1, "los trabajos de élite pagan más que los de entrada");
    }
}
