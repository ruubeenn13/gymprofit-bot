package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.MiembroEmpresa;
import com.gymprofit.bot.services.EmpresaService.ResultadoContratar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica el toggle de la bolsa de empleo ({@link EmpresaService#alternarContratando}) con el
 * repositorio mockeado. Solo cubre la autorización (alto cargo) y que el nuevo valor es el <b>opuesto</b>
 * al actual, comportamiento real vía {@code verify(fijarContratando)}. Como el método no toca personajes,
 * economía ni trabajos, esas dependencias se pasan {@code null} (nunca se usan en esta ruta).
 */
class EmpresaServiceContratandoTest {

    private final EmpresaRepositorio repo = mock(EmpresaRepositorio.class);

    private static final Instant AHORA = Instant.parse("2026-07-24T12:00:00Z");
    private static final long EMPRESA_ID = 10L;
    private static final long DUENO = 1L;
    private static final long EMPLEADO = 3L;

    private EmpresaService svc() {
        return new EmpresaService(repo, null, null, null);
    }

    @Test
    @DisplayName("cerrada -> abierta: el dueño la enciende y se persiste TRUE")
    void ciarraAbierta() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(false)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        assertEquals(ResultadoContratar.ABIERTA, svc().alternarContratando(DUENO));
        // El nuevo valor es el opuesto al actual (estaba a false): se persiste true.
        verify(repo).fijarContratando(EMPRESA_ID, true);
    }

    @Test
    @DisplayName("abierta -> cerrada: se persiste FALSE")
    void abiertaCerrada() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(true)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        assertEquals(ResultadoContratar.CERRADA, svc().alternarContratando(DUENO));
        verify(repo).fijarContratando(EMPRESA_ID, false);
    }

    @Test
    @DisplayName("un empleado no puede: NO_AUTORIZADO y no toca el flag")
    void empleadoNoAutorizado() {
        when(repo.deMiembro(EMPLEADO)).thenReturn(Optional.of(empresa(false)));
        // altosCargos filtra en SQL a DUENO/DIRECTIVO: un empleado no aparece.
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of());

        assertEquals(ResultadoContratar.NO_AUTORIZADO, svc().alternarContratando(EMPLEADO));
        verify(repo, never()).fijarContratando(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("sin empresa: SIN_EMPRESA y no toca el flag")
    void sinEmpresa() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.empty());

        assertEquals(ResultadoContratar.SIN_EMPRESA, svc().alternarContratando(DUENO));
        verify(repo, never()).fijarContratando(anyLong(), anyBoolean());
    }

    // ------------------------------------------------------------------ helpers

    private static Empresa empresa(boolean contratando) {
        return new Empresa(EMPRESA_ID, "HIERRO", DUENO, "Acme", 1, 0L, AHORA, null, 0L, 0, contratando);
    }

    private static MiembroEmpresa miembro(long discordId, RangoEmpresa rango) {
        return new MiembroEmpresa(EMPRESA_ID, discordId, rango, AHORA);
    }
}
