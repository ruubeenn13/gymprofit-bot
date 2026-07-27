package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica {@link CuotaEmpresasService} con el repositorio mockeado: el líder de rama (más prestigio)
 * vende con prima, el rezagado con penalización, y sin competencia (una sola empresa en su rama) el
 * factor queda neutro en 1.0.
 */
class CuotaEmpresasServiceTest {

    private final EmpresaRepositorio repo = mock(EmpresaRepositorio.class);

    private static final Instant AHORA = Instant.parse("2026-07-27T12:00:00Z");

    // Empresa de test: todos los args del record, con canalId null y mercancia 0, sin prestamo (deuda/cuota 0).
    private static Empresa empresa(String nombre, String rama, int nivel, long bote) {
        return new Empresa(1L, rama, 1L, nombre, nivel, bote, AHORA, null, 0L, 0, false, 0L, 0L);
    }

    @Test
    @DisplayName("líder de rama tiene factor > 1; rezagado < 1")
    void liderYRezagado() {
        // A nivel 5 bote 0 miembros 0 → prestigio 50000; B nivel 1 → 10000; total 60000, n=2
        when(repo.rankingDeRama("NEGOCIOS")).thenReturn(List.of(
                new EmpresaRepositorio.EmpresaRanking("A", "NEGOCIOS", 5, 0, 0),
                new EmpresaRepositorio.EmpresaRanking("B", "NEGOCIOS", 1, 0, 0)));
        CuotaEmpresasService svc = new CuotaEmpresasService(repo);
        assertTrue(svc.factorVentaDe(empresa("A", "NEGOCIOS", 5, 0)) > 1.0);
        assertTrue(svc.factorVentaDe(empresa("B", "NEGOCIOS", 1, 0)) < 1.0);
    }

    @Test
    @DisplayName("empresa sola en su rama → factor 1.0 (sin competencia)")
    void monopolio() {
        when(repo.rankingDeRama("ARTE")).thenReturn(List.of(
                new EmpresaRepositorio.EmpresaRanking("U", "ARTE", 3, 0, 0)));
        assertEquals(1.0, new CuotaEmpresasService(repo).factorVentaDe(empresa("U", "ARTE", 3, 0)), 1e-9);
    }

    @Test
    @DisplayName("cuotaDe: proporción propio/total; rama vacía (total 0) → 0")
    void cuotaDe() {
        // Mismo reparto que liderYRezagado: A prestigio 50000, B 10000, total 60000.
        when(repo.rankingDeRama("NEGOCIOS")).thenReturn(List.of(
                new EmpresaRepositorio.EmpresaRanking("A", "NEGOCIOS", 5, 0, 0),
                new EmpresaRepositorio.EmpresaRanking("B", "NEGOCIOS", 1, 0, 0)));
        CuotaEmpresasService svc = new CuotaEmpresasService(repo);
        assertEquals(50000.0 / 60000.0, svc.cuotaDe(empresa("A", "NEGOCIOS", 5, 0)), 1e-9);

        when(repo.rankingDeRama("VACIA")).thenReturn(List.of());
        assertEquals(0, svc.cuotaDe(empresa("X", "VACIA", 1, 0)), 1e-9);
    }
}
