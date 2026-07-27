package com.gymprofit.bot.jobs;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica el cálculo de la espera hasta el próximo jueves 02:00 de Europe/Madrid: que apunta al jueves
 * correcto desde un día cualquiera de la semana y que el propio jueves discrimina bien antes/después de
 * las 02:00 (mismo jueves vs. el siguiente).
 */
class DividendoEmpresasJobTest {

    @Test
    void esperaApuntaAlProximoJueves0200() {
        ZonedDateTime lunes = ZonedDateTime.of(2026, 7, 20, 10, 0, 0, 0, ZoneId.of("Europe/Madrid"));
        ZonedDateTime destino = lunes.plus(DividendoEmpresasJob.esperaHastaProximoJueves(lunes));
        assertEquals(DayOfWeek.THURSDAY, destino.getDayOfWeek());
        assertEquals(LocalTime.of(2, 0), destino.toLocalTime());
    }

    @Test
    void juevesAntesDeLasDosVaHoy_despuesVaLaSemanaQueViene() {
        ZoneId z = ZoneId.of("Europe/Madrid");
        ZonedDateTime juevesTemprano = ZonedDateTime.of(2026, 7, 23, 1, 0, 0, 0, z); // 2026-07-23 es jueves
        ZonedDateTime d1 = juevesTemprano.plus(DividendoEmpresasJob.esperaHastaProximoJueves(juevesTemprano));
        assertEquals(juevesTemprano.toLocalDate(), d1.toLocalDate()); // hoy 02:00
        ZonedDateTime juevesTarde = ZonedDateTime.of(2026, 7, 23, 3, 0, 0, 0, z);
        ZonedDateTime d2 = juevesTarde.plus(DividendoEmpresasJob.esperaHastaProximoJueves(juevesTarde));
        assertEquals(DayOfWeek.THURSDAY, d2.getDayOfWeek());
        assertEquals(juevesTarde.toLocalDate().plusWeeks(1), d2.toLocalDate()); // jueves que viene
    }
}
