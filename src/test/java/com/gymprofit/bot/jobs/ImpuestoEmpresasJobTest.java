package com.gymprofit.bot.jobs;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica el cálculo de la espera hasta el próximo lunes 02:00 de Europe/Madrid: que apunta al lunes
 * correcto desde un día cualquiera de la semana y que el propio lunes discrimina bien antes/después de
 * las 02:00 (mismo lunes vs. el siguiente).
 */
class ImpuestoEmpresasJobTest {

    @Test
    void esperaApuntaAlProximoLunes0200() {
        ZonedDateTime miercoles = ZonedDateTime.of(2026, 7, 22, 10, 0, 0, 0, ZoneId.of("Europe/Madrid"));
        ZonedDateTime destino = miercoles.plus(ImpuestoEmpresasJob.esperaHastaProximoLunes(miercoles));
        assertEquals(DayOfWeek.MONDAY, destino.getDayOfWeek());
        assertEquals(LocalTime.of(2, 0), destino.toLocalTime());
    }

    @Test
    void lunesAntesDeLasDosVaHoy_despuesVaLaSemanaQueViene() {
        ZoneId z = ZoneId.of("Europe/Madrid");
        ZonedDateTime lunesTemprano = ZonedDateTime.of(2026, 7, 20, 1, 0, 0, 0, z); // 2026-07-20 es lunes
        ZonedDateTime d1 = lunesTemprano.plus(ImpuestoEmpresasJob.esperaHastaProximoLunes(lunesTemprano));
        assertEquals(lunesTemprano.toLocalDate(), d1.toLocalDate()); // hoy 02:00
        ZonedDateTime lunesTarde = ZonedDateTime.of(2026, 7, 20, 3, 0, 0, 0, z);
        ZonedDateTime d2 = lunesTarde.plus(ImpuestoEmpresasJob.esperaHastaProximoLunes(lunesTarde));
        assertEquals(DayOfWeek.MONDAY, d2.getDayOfWeek());
        assertEquals(lunesTarde.toLocalDate().plusWeeks(1), d2.toLocalDate()); // lunes que viene
    }
}
