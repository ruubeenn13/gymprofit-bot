package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.MiembroEmpresa;
import com.gymprofit.bot.services.EmpresaVentaService.Estado;
import com.gymprofit.bot.services.EmpresaVentaService.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica {@link EmpresaVentaService} con el repositorio mockeado. Es dinero: se comprueba el
 * <b>gate atomico</b> (nunca se abona al bote una venta que no pudo descontar mercancia), que el
 * <b>impuesto se quema</b> (solo el neto entra al bote) y las cifras exactas del resultado. Se
 * verifica comportamiento real (verify de gastarMercancia/incrementarBote), no ecos de mock.
 */
class EmpresaVentaServiceTest {

    private final EmpresaRepositorio repo = mock(EmpresaRepositorio.class);

    private static final Instant AHORA = Instant.parse("2026-07-24T12:00:00Z");
    private static final long EMPRESA_ID = 10L;
    private static final long DUENO = 1L;
    private static final long EMPLEADO = 3L;

    private EmpresaVentaService svc() {
        return new EmpresaVentaService(repo);
    }

    @Test
    @DisplayName("venta OK: quema el impuesto y abona SOLO el neto al bote")
    void ventaOkQuemaImpuestoYAbonaNeto() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(100L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));
        when(repo.gastarMercancia(EMPRESA_ID, 100L)).thenReturn(true);

        Resultado r = svc().vender(DUENO, OptionalLong.of(100L));

        assertEquals(Estado.OK, r.estado());
        assertEquals(100L, r.unidades());
        assertEquals(5_000L, r.bruto());
        assertEquals(750L, r.impuesto());
        assertEquals(4_250L, r.neto());
        assertEquals(0L, r.restante());
        verify(repo).gastarMercancia(EMPRESA_ID, 100L);
        // El impuesto NO se ingresa a nadie: al bote solo va el neto (5.000 − 750 = 4.250).
        verify(repo).incrementarBote(EMPRESA_ID, 4_250L);
    }

    @Test
    @DisplayName("sin cantidad vende toda la mercancia actual")
    void ventaSinCantidadVendeTodo() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(100L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));
        when(repo.gastarMercancia(EMPRESA_ID, 100L)).thenReturn(true);

        Resultado r = svc().vender(DUENO, OptionalLong.empty());

        assertEquals(Estado.OK, r.estado());
        assertEquals(100L, r.unidades());
        assertEquals(0L, r.restante());
        verify(repo).gastarMercancia(EMPRESA_ID, 100L);
        verify(repo).incrementarBote(EMPRESA_ID, 4_250L);
    }

    @Test
    @DisplayName("venta parcial: gasta lo pedido y deja el resto en almacen")
    void ventaParcial() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(100L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));
        when(repo.gastarMercancia(EMPRESA_ID, 40L)).thenReturn(true);

        Resultado r = svc().vender(DUENO, OptionalLong.of(40L));

        assertEquals(Estado.OK, r.estado());
        assertEquals(40L, r.unidades());
        assertEquals(2_000L, r.bruto());
        assertEquals(300L, r.impuesto());
        assertEquals(1_700L, r.neto()); // 40 * 50 * 0,85
        assertEquals(60L, r.restante());
        verify(repo).gastarMercancia(EMPRESA_ID, 40L);
        verify(repo).incrementarBote(EMPRESA_ID, 1_700L);
    }

    @Test
    @DisplayName("cantidad > disponible: se acota con Math.min y vende solo lo que hay")
    void ventaCantidadMayorQueDisponibleVendeLoQueHay() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(100L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));
        when(repo.gastarMercancia(EMPRESA_ID, 100L)).thenReturn(true);

        Resultado r = svc().vender(DUENO, OptionalLong.of(200L));

        assertEquals(Estado.OK, r.estado());
        assertEquals(100L, r.unidades()); // pidió 200 pero solo hay 100: Math.min gana
        assertEquals(0L, r.restante());
        verify(repo).gastarMercancia(EMPRESA_ID, 100L);
        verify(repo).incrementarBote(EMPRESA_ID, 4_250L);
    }

    @Test
    @DisplayName("almacen vacio: SIN_MERCANCIA y no toca el bote")
    void ventaSinMercancia() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(0L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        Resultado r = svc().vender(DUENO, OptionalLong.empty());

        assertEquals(Estado.SIN_MERCANCIA, r.estado());
        verify(repo, never()).incrementarBote(anyLong(), anyLong());
    }

    @Test
    @DisplayName("un empleado no puede vender: NO_AUTORIZADO y ni siquiera intenta descontar")
    void ventaNoAutorizadoSiNoEsAltoCargo() {
        when(repo.deMiembro(EMPLEADO)).thenReturn(Optional.of(empresa(100L)));
        // altosCargos filtra en SQL a DUENO/DIRECTIVO: un empleado no aparece → no es alto cargo.
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of());

        Resultado r = svc().vender(EMPLEADO, OptionalLong.of(100L));

        assertEquals(Estado.NO_AUTORIZADO, r.estado());
        verify(repo, never()).gastarMercancia(anyLong(), anyLong());
        verify(repo, never()).incrementarBote(anyLong(), anyLong());
    }

    @Test
    @DisplayName("el actor no esta en ninguna empresa: SIN_EMPRESA")
    void ventaSinEmpresa() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.empty());

        Resultado r = svc().vender(DUENO, OptionalLong.empty());

        assertEquals(Estado.SIN_EMPRESA, r.estado());
        verify(repo, never()).gastarMercancia(anyLong(), anyLong());
        verify(repo, never()).incrementarBote(anyLong(), anyLong());
    }

    @Test
    @DisplayName("carrera perdida: gastarMercancia false NO abona al bote (gate atomico)")
    void gastarMercanciaFalseNoAbona() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(100L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));
        when(repo.gastarMercancia(EMPRESA_ID, 100L)).thenReturn(false);

        Resultado r = svc().vender(DUENO, OptionalLong.of(100L));

        assertEquals(Estado.SIN_MERCANCIA, r.estado());
        verify(repo, never()).incrementarBote(anyLong(), anyLong());
    }

    // ------------------------------------------------------------------ helpers

    private static Empresa empresa(long mercancia) {
        return new Empresa(EMPRESA_ID, "HIERRO", DUENO, "Acme", 1, 0L, AHORA, null, mercancia, 0);
    }

    private static MiembroEmpresa miembro(long discordId, RangoEmpresa rango) {
        return new MiembroEmpresa(EMPRESA_ID, discordId, rango, AHORA);
    }
}
