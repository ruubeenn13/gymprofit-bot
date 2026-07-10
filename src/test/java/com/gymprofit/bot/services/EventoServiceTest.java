package com.gymprofit.bot.services;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el parseo de la fecha de {@code /evento} ({@link EventoService#parsearFecha(String)}):
 * acepta el formato documentado y rechaza el resto sin lanzar excepción.
 */
class EventoServiceTest {

    @Test
    void parseaLaFechaEnElFormatoEsperado() {
        Optional<LocalDateTime> fecha = EventoService.parsearFecha("2026-07-20 18:30");
        assertTrue(fecha.isPresent());
        assertEquals(LocalDateTime.of(2026, 7, 20, 18, 30), fecha.get());
    }

    @Test
    void toleraEspaciosAlrededor() {
        assertTrue(EventoService.parsearFecha("  2026-07-20 18:30  ").isPresent());
    }

    @Test
    void rechazaFormatosInvalidos() {
        assertTrue(EventoService.parsearFecha("mañana").isEmpty());
        assertTrue(EventoService.parsearFecha("2026-07-20").isEmpty(), "falta la hora");
        assertTrue(EventoService.parsearFecha("2026-13-40 99:99").isEmpty(), "valores fuera de rango");
    }
}
