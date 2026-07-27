package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del catálogo puro de {@link EventoEconomico} (F5): cada palanca no tocada por un evento
 * concreto queda en su valor neutro, el catálogo está balanceado 4 buenos / 4 malos y
 * {@link EventoEconomico#aleatorio(double)} mapea el rango [0,1) a un evento siempre válido.
 */
class EventoEconomicoTest {

    @Test
    @DisplayName("cada palanca no tocada es neutra (1.0 / NINGUNO)")
    void palancasNeutrasPorDefecto() {
        EventoEconomico e = EventoEconomico.BOOM_CONSUMO;
        assertEquals(1.30, e.ventaMult());
        assertEquals(1.0, e.produccionMult());
        assertEquals(1.0, e.impuestoMult());
        assertEquals(1.0, e.curroMult());
        assertEquals(EventoEconomico.BolsaSesgo.NINGUNO, e.bolsaSesgo());
    }

    @Test
    @DisplayName("catálogo balanceado: 4 buenos y 4 malos")
    void catalogoBalanceado() {
        long buenos = java.util.Arrays.stream(EventoEconomico.values())
                .filter(e -> e.tono() == EventoEconomico.Tono.BUENO).count();
        long malos = java.util.Arrays.stream(EventoEconomico.values())
                .filter(e -> e.tono() == EventoEconomico.Tono.MALO).count();
        assertEquals(4, buenos);
        assertEquals(4, malos);
    }

    @Test
    @DisplayName("aleatorio: 0.0 da el primero, cerca de 1.0 da el último, siempre en rango")
    void aleatorioEnRango() {
        EventoEconomico[] v = EventoEconomico.values();
        assertEquals(v[0], EventoEconomico.aleatorio(0.0));
        assertEquals(v[v.length - 1], EventoEconomico.aleatorio(0.999));
        for (double a = 0; a < 1; a += 0.05) {
            assertNotNull(EventoEconomico.aleatorio(a));
        }
    }
}
