package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.MiembroEmpresa;
import com.gymprofit.bot.services.PrestamoEmpresasService.EstadoConceder;
import com.gymprofit.bot.services.PrestamoEmpresasService.EstadoPago;
import com.gymprofit.bot.services.PrestamoEmpresasService.ResultadoConceder;
import com.gymprofit.bot.services.PrestamoEmpresasService.ResultadoPago;
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
 * Verifica {@link PrestamoEmpresasService} con el repositorio mockeado. Es dinero: se comprueba que el
 * principal entra con {@code incrementarBote} y la deuda/cuota se fija justo después (conceder), que la
 * amortizacion usa {@code gastarDelBote} como <b>gate</b> (nunca baja la deuda sin descontar del bote),
 * que solo hay un prestamo a la vez y que se respeta el limite por nivel. Se verifica comportamiento
 * real (verify de incrementarBote/gastarDelBote/fijarPrestamo), no ecos de mock.
 */
class PrestamoEmpresasServiceTest {

    private final EmpresaRepositorio repo = mock(EmpresaRepositorio.class);

    private static final Instant AHORA = Instant.parse("2026-07-24T12:00:00Z");
    private static final long EMPRESA_ID = 10L;
    private static final long DUENO = 1L;
    private static final long EMPLEADO = 3L;

    private PrestamoEmpresasService svc() {
        return new PrestamoEmpresasService(repo);
    }

    // ------------------------------------------------------------------ conceder

    @Test
    @DisplayName("conceder OK: el principal entra al bote y se fija deuda+cuota con interes")
    void concederOk() {
        // nivel 1 → limite 20 000; principal 10 000 → deuda 12 000 (interes 20 %), cuota 3 000 (plazo 4).
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 0L, 0L, 0L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        ResultadoConceder r = svc().conceder(DUENO, 10_000L);

        assertEquals(EstadoConceder.OK, r.estado());
        assertEquals(10_000L, r.principal());
        assertEquals(12_000L, r.deuda());
        assertEquals(3_000L, r.cuota());
        assertEquals(20_000L, r.limite());
        verify(repo).incrementarBote(EMPRESA_ID, 10_000L);
        verify(repo).fijarPrestamo(EMPRESA_ID, 12_000L, 3_000L);
    }

    @Test
    @DisplayName("conceder SIN_EMPRESA: no toca el bote ni el prestamo")
    void concederSinEmpresa() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.empty());

        ResultadoConceder r = svc().conceder(DUENO, 10_000L);

        assertEquals(EstadoConceder.SIN_EMPRESA, r.estado());
        verify(repo, never()).incrementarBote(anyLong(), anyLong());
        verify(repo, never()).fijarPrestamo(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("conceder NO_AUTORIZADO: un empleado no puede pedir prestamo")
    void concederNoAutorizado() {
        when(repo.deMiembro(EMPLEADO)).thenReturn(Optional.of(empresa(1, 0L, 0L, 0L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of());

        ResultadoConceder r = svc().conceder(EMPLEADO, 10_000L);

        assertEquals(EstadoConceder.NO_AUTORIZADO, r.estado());
        verify(repo, never()).incrementarBote(anyLong(), anyLong());
        verify(repo, never()).fijarPrestamo(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("conceder CANTIDAD_INVALIDA: cantidad <= 0")
    void concederCantidadInvalida() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 0L, 0L, 0L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        ResultadoConceder r = svc().conceder(DUENO, 0L);

        assertEquals(EstadoConceder.CANTIDAD_INVALIDA, r.estado());
        verify(repo, never()).incrementarBote(anyLong(), anyLong());
        verify(repo, never()).fijarPrestamo(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("conceder YA_TIENE_PRESTAMO: la empresa ya arrastra deuda")
    void concederYaTienePrestamo() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 0L, 12_000L, 3_000L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        ResultadoConceder r = svc().conceder(DUENO, 5_000L);

        assertEquals(EstadoConceder.YA_TIENE_PRESTAMO, r.estado());
        verify(repo, never()).incrementarBote(anyLong(), anyLong());
        verify(repo, never()).fijarPrestamo(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("conceder LIMITE: por encima del maximo del nivel y devuelve el limite")
    void concederLimite() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 0L, 0L, 0L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        ResultadoConceder r = svc().conceder(DUENO, 20_001L); // nivel 1 → limite 20 000

        assertEquals(EstadoConceder.LIMITE, r.estado());
        assertEquals(20_000L, r.limite());
        verify(repo, never()).incrementarBote(anyLong(), anyLong());
        verify(repo, never()).fijarPrestamo(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("conceder con cantidad == limite: el borde pasa (OK, no LIMITE)")
    void concederCantidadIgualAlLimite_ok() {
        // nivel 1 → limite 20 000; pedir EXACTAMENTE el limite debe conceder (la comparacion es cantidad > limite).
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 0L, 0L, 0L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        long limite = Prestamo.limite(1);
        ResultadoConceder r = svc().conceder(DUENO, limite);

        assertEquals(EstadoConceder.OK, r.estado());
        assertEquals(limite, r.principal());
        verify(repo).incrementarBote(EMPRESA_ID, limite);
        verify(repo).fijarPrestamo(EMPRESA_ID,
                Prestamo.deudaConInteres(limite), Prestamo.cuota(Prestamo.deudaConInteres(limite)));
    }

    // ------------------------------------------------------------------ pagar

    @Test
    @DisplayName("pagar con cantidad: amortiza min(cantidad, deuda) del bote y baja la deuda")
    void pagarConCantidad() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 8_000L, 12_000L, 3_000L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));
        when(repo.gastarDelBote(EMPRESA_ID, 5_000L)).thenReturn(true);

        ResultadoPago r = svc().pagar(DUENO, OptionalLong.of(5_000L));

        assertEquals(EstadoPago.OK, r.estado());
        assertEquals(5_000L, r.pagado());
        assertEquals(7_000L, r.deudaRestante());
        assertEquals(3_000L, r.cuota());
        verify(repo).gastarDelBote(EMPRESA_ID, 5_000L);
        verify(repo).fijarPrestamo(EMPRESA_ID, 7_000L, 3_000L);
    }

    @Test
    @DisplayName("pagar sin cantidad: amortiza min(deuda, bote) del bote")
    void pagarSinCantidad() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 8_000L, 12_000L, 3_000L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));
        when(repo.gastarDelBote(EMPRESA_ID, 8_000L)).thenReturn(true);

        ResultadoPago r = svc().pagar(DUENO, OptionalLong.empty());

        assertEquals(EstadoPago.OK, r.estado());
        assertEquals(8_000L, r.pagado());
        assertEquals(4_000L, r.deudaRestante());
        assertEquals(3_000L, r.cuota());
        verify(repo).gastarDelBote(EMPRESA_ID, 8_000L);
        verify(repo).fijarPrestamo(EMPRESA_ID, 4_000L, 3_000L);
    }

    @Test
    @DisplayName("pago que salda: paga toda la deuda y deja deuda y cuota a 0")
    void pagarSalda() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 20_000L, 12_000L, 3_000L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));
        when(repo.gastarDelBote(EMPRESA_ID, 12_000L)).thenReturn(true);

        ResultadoPago r = svc().pagar(DUENO, OptionalLong.of(12_000L));

        assertEquals(EstadoPago.OK, r.estado());
        assertEquals(12_000L, r.pagado());
        assertEquals(0L, r.deudaRestante());
        assertEquals(0L, r.cuota());
        verify(repo).gastarDelBote(EMPRESA_ID, 12_000L);
        verify(repo).fijarPrestamo(EMPRESA_ID, 0L, 0L);
    }

    @Test
    @DisplayName("pagar SIN_DEUDA: la empresa no tiene prestamo")
    void pagarSinDeuda() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 8_000L, 0L, 0L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        ResultadoPago r = svc().pagar(DUENO, OptionalLong.empty());

        assertEquals(EstadoPago.SIN_DEUDA, r.estado());
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
        verify(repo, never()).fijarPrestamo(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("pagar NO_AUTORIZADO: un empleado no puede amortizar")
    void pagarNoAutorizado() {
        when(repo.deMiembro(EMPLEADO)).thenReturn(Optional.of(empresa(1, 8_000L, 12_000L, 3_000L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of());

        ResultadoPago r = svc().pagar(EMPLEADO, OptionalLong.of(5_000L));

        assertEquals(EstadoPago.NO_AUTORIZADO, r.estado());
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
        verify(repo, never()).fijarPrestamo(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("pagar SIN_EMPRESA: no toca nada")
    void pagarSinEmpresa() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.empty());

        ResultadoPago r = svc().pagar(DUENO, OptionalLong.of(5_000L));

        assertEquals(EstadoPago.SIN_EMPRESA, r.estado());
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
        verify(repo, never()).fijarPrestamo(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("pagar SIN_FONDOS: gastarDelBote false NO baja la deuda (gate)")
    void pagarSinFondosGate() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 3_000L, 12_000L, 3_000L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));
        when(repo.gastarDelBote(EMPRESA_ID, 5_000L)).thenReturn(false); // carrera perdida / sin saldo

        ResultadoPago r = svc().pagar(DUENO, OptionalLong.of(5_000L));

        assertEquals(EstadoPago.SIN_FONDOS, r.estado());
        verify(repo, never()).fijarPrestamo(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("pagar sin cantidad y bote 0: SIN_FONDOS sin intentar descontar")
    void pagarSinCantidadBoteVacio() {
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 0L, 12_000L, 3_000L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));

        ResultadoPago r = svc().pagar(DUENO, OptionalLong.empty());

        assertEquals(EstadoPago.SIN_FONDOS, r.estado());
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
        verify(repo, never()).fijarPrestamo(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("pagar cantidad > deuda: se acota a la deuda (Math.min) y salda el prestamo")
    void pagarCantidadMayorQueDeuda_pagaSoloLaDeuda() {
        // deuda 6 000, se pide pagar 9 999: el cap Math.min(cantidad, deuda) descuenta SOLO 6 000 y salda.
        when(repo.deMiembro(DUENO)).thenReturn(Optional.of(empresa(1, 20_000L, 6_000L, 2_000L)));
        when(repo.altosCargos(EMPRESA_ID)).thenReturn(List.of(miembro(DUENO, RangoEmpresa.DUENO)));
        when(repo.gastarDelBote(EMPRESA_ID, 6_000L)).thenReturn(true);

        ResultadoPago r = svc().pagar(DUENO, OptionalLong.of(9_999L));

        assertEquals(EstadoPago.OK, r.estado());
        assertEquals(6_000L, r.pagado());
        assertEquals(0L, r.deudaRestante());
        assertEquals(0L, r.cuota());
        verify(repo).gastarDelBote(EMPRESA_ID, 6_000L); // NO 9 999: nunca se paga de mas
        verify(repo).fijarPrestamo(EMPRESA_ID, 0L, 0L);
    }

    // ------------------------------------------------------------------ helpers

    private static Empresa empresa(int nivel, long bote, long deuda, long cuota) {
        return new Empresa(EMPRESA_ID, "HIERRO", DUENO, "Acme", nivel, bote, AHORA, null,
                0L, 0, false, deuda, cuota);
    }

    private static MiembroEmpresa miembro(long discordId, RangoEmpresa rango) {
        return new MiembroEmpresa(EMPRESA_ID, discordId, rango, AHORA);
    }
}
