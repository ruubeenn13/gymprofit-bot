package com.gymprofit.bot.services;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica la elección de ganadores: cantidad, sin repetición, subconjunto y casos límite. */
class SorteoServiceTest {

    private static final List<Long> PARTICIPANTES = List.of(1L, 2L, 3L, 4L, 5L);

    @Test
    void eligeElNumeroPedidoSinRepetir() {
        List<Long> ganadores = SorteoService.elegirGanadores(PARTICIPANTES, 3, new Random(42));
        assertEquals(3, ganadores.size());
        assertEquals(3, ganadores.stream().distinct().count(), "sin repetidos");
        assertTrue(PARTICIPANTES.containsAll(ganadores), "todos son participantes");
    }

    @Test
    void nuncaMasGanadoresQueParticipantes() {
        List<Long> ganadores = SorteoService.elegirGanadores(PARTICIPANTES, 99, new Random(1));
        assertEquals(PARTICIPANTES.size(), ganadores.size());
    }

    @Test
    void sinParticipantesODevuelveVacio() {
        assertTrue(SorteoService.elegirGanadores(List.of(), 3, new Random()).isEmpty());
        assertTrue(SorteoService.elegirGanadores(PARTICIPANTES, 0, new Random()).isEmpty());
    }
}
