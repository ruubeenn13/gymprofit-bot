package com.gymprofit.bot.services;

import com.gymprofit.bot.db.MiembroEmpresa;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la función pura {@link RepartoNomina#calcular}: el reparto del pool de la nómina por peso
 * de rango, con redondeo a la baja. Sin BD ni mocks: solo aritmética sobre listas de miembros.
 */
class RepartoNominaTest {

    /** Miembro de prueba: solo importan el discordId y el rango para el reparto. */
    private static MiembroEmpresa miembro(long discordId, RangoEmpresa rango) {
        return new MiembroEmpresa(1L, discordId, rango, Instant.EPOCH);
    }

    @Test
    void unSoloMiembroSeLlevaTodoElPool() {
        // pool = floor(1000 * 0.20) = 200; un único peso ⇒ se lleva el pool entero.
        List<RepartoNomina.ParteNomina> partes = RepartoNomina.calcular(1000, List.of(
                miembro(10L, RangoEmpresa.DUENO)));

        assertEquals(1, partes.size());
        assertEquals(10L, partes.get(0).discordId());
        assertEquals(200L, partes.get(0).parte());
    }

    @Test
    void reparteProporcionalAlPesoDelRango() {
        // pool = floor(6000 * 0.20) = 1200. Pesos: DUENO=5, BECARIO=1 ⇒ suma 6.
        // dueño = floor(1200*5/6) = 1000; becario = floor(1200*1/6) = 200.
        List<RepartoNomina.ParteNomina> partes = RepartoNomina.calcular(6000, List.of(
                miembro(10L, RangoEmpresa.DUENO),
                miembro(20L, RangoEmpresa.BECARIO)));

        long dueno = parteDe(partes, 10L);
        long becario = parteDe(partes, 20L);
        assertEquals(1000L, dueno);
        assertEquals(200L, becario);
        assertTrue(dueno > becario, "el dueño (peso 5) cobra más que el becario (peso 1)");
    }

    @Test
    void parteMenorQueUnCoinRedondeaACero() {
        // pool = floor(25 * 0.20) = 5. Pesos: DUENO=5, BECARIO=1 ⇒ suma 6.
        // becario = floor(5*1/6) = floor(0.83) = 0; dueño = floor(5*5/6) = 4.
        List<RepartoNomina.ParteNomina> partes = RepartoNomina.calcular(25, List.of(
                miembro(10L, RangoEmpresa.DUENO),
                miembro(20L, RangoEmpresa.BECARIO)));

        assertEquals(4L, parteDe(partes, 10L));
        assertEquals(0L, parteDe(partes, 20L));
    }

    @Test
    void boteCeroDaPartesCero() {
        List<RepartoNomina.ParteNomina> partes = RepartoNomina.calcular(0, List.of(
                miembro(10L, RangoEmpresa.DUENO),
                miembro(20L, RangoEmpresa.BECARIO)));

        assertEquals(0L, parteDe(partes, 10L));
        assertEquals(0L, parteDe(partes, 20L));
    }

    @Test
    void sinMiembrosDaListaVacia() {
        assertTrue(RepartoNomina.calcular(1000, List.of()).isEmpty());
    }

    private static long parteDe(List<RepartoNomina.ParteNomina> partes, long discordId) {
        return partes.stream()
                .filter(p -> p.discordId() == discordId)
                .mapToLong(RepartoNomina.ParteNomina::parte)
                .findFirst()
                .orElseThrow();
    }
}
