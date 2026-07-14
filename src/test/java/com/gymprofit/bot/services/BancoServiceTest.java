package com.gymprofit.bot.services;

import com.gymprofit.bot.db.BancoCuenta;
import com.gymprofit.bot.db.BancoRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.BancoService.Estado;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica el interés (puro) y las operaciones del banco (depósito, retiro, préstamo, pago). */
class BancoServiceTest {

    private final BancoRepositorio banco = mock(BancoRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final BancoService servicio = new BancoService(banco, economia, usuarios);

    private void cuenta(long saldo, long prestamo) {
        when(banco.obtenerOCrear(1L)).thenReturn(new BancoCuenta(1L, saldo, prestamo, null));
    }

    // ---------------------- Interés (puro) ----------------------

    @Test
    void interesConTopeYSinFechaOSaldo() {
        LocalDate hoy = LocalDate.of(2026, 1, 20);
        assertEquals(0, BancoService.interesGanado(0, hoy.minusDays(5), hoy));   // sin ahorro
        assertEquals(0, BancoService.interesGanado(1000, null, hoy));            // primera vez
        assertEquals(20, BancoService.interesGanado(1000, hoy.minusDays(1), hoy)); // 2% de 1000
        assertEquals(200, BancoService.interesGanado(1000, hoy.minusDays(10), hoy));
        assertEquals(500, BancoService.interesGanado(1_000_000, hoy.minusDays(1), hoy)); // tope diario
    }

    // ---------------------- Operaciones ----------------------

    @Test
    void depositarMueveDelMonederoAlAhorro() {
        cuenta(0, 0);
        when(economia.gastar(eq(1L), eq(100L), anyString())).thenReturn(true);
        var r = servicio.depositar(1L, 100);
        assertEquals(Estado.OK, r.estado());
        assertEquals(100, r.valor());
        verify(economia).gastar(eq(1L), eq(100L), anyString());
    }

    @Test
    void depositarSinSaldoFalla() {
        cuenta(0, 0);
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(false);
        assertEquals(Estado.SIN_SALDO, servicio.depositar(1L, 100).estado());
    }

    @Test
    void retirarDelAhorroPagaAlMonedero() {
        cuenta(500, 0);
        var r = servicio.retirar(1L, 200);
        assertEquals(Estado.OK, r.estado());
        verify(economia).ingresar(eq(1L), eq(200L), anyString());
    }

    @Test
    void retirarMasDeLoAhorradoFalla() {
        cuenta(100, 0);
        assertEquals(Estado.SIN_FONDOS, servicio.retirar(1L, 500).estado());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }

    @Test
    void prestamoDaCoinsYContraeDeudaConComision() {
        cuenta(0, 0);
        when(usuarios.buscar(1L)).thenReturn(Optional.of(
                new UsuarioDiscord(1L, 0, 4, 0, 0, null, null, false))); // límite 1000 + 4*500 = 3000
        var r = servicio.prestamo(1L, 1000);
        assertEquals(Estado.OK, r.estado());
        assertEquals(1100, r.valor()); // deuda = 1000 * 1.10
        verify(economia).ingresar(eq(1L), eq(1000L), anyString());
    }

    @Test
    void noSePidePrestamoConDeudaNiPorEncimaDelLimite() {
        cuenta(0, 800);
        assertEquals(Estado.YA_DEUDA, servicio.prestamo(1L, 100).estado());

        cuenta(0, 0);
        when(usuarios.buscar(1L)).thenReturn(Optional.of(
                new UsuarioDiscord(1L, 0, 0, 0, 0, null, null, false))); // límite 1000
        var r = servicio.prestamo(1L, 5000);
        assertEquals(Estado.SUPERA_LIMITE, r.estado());
        assertEquals(1000, r.valor());
    }

    @Test
    void pagarReduceLaDeuda() {
        cuenta(0, 1100);
        when(economia.gastar(eq(1L), eq(400L), anyString())).thenReturn(true);
        var r = servicio.pagar(1L, 400);
        assertEquals(Estado.OK, r.estado());
        assertEquals(700, r.valor()); // restante
    }

    @Test
    void pagarSinDeudaFalla() {
        cuenta(0, 0);
        assertEquals(Estado.SIN_DEUDA, servicio.pagar(1L, 100).estado());
    }
}
