package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.RegaloService.Estado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica las transferencias entre jugadores (regalar coins e ítems). */
class RegaloServiceTest {

    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final RegaloService servicio = new RegaloService(economia, inventario, usuarios);

    @Test
    void regalarCoinsMueveDelDadorAlReceptor() {
        when(economia.gastar(eq(1L), eq(100L), anyString())).thenReturn(true);
        assertEquals(Estado.OK, servicio.regalarCoins(1L, 2L, 100));
        verify(economia).gastar(eq(1L), eq(100L), anyString());
        verify(economia).ingresar(eq(2L), eq(100L), anyString());
    }

    @Test
    void sinSaldoNoRegala() {
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(false);
        assertEquals(Estado.SIN_SALDO, servicio.regalarCoins(1L, 2L, 100));
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }

    @Test
    void noSePuedeRegalarASiMismoNiCantidadInvalida() {
        assertEquals(Estado.A_TI_MISMO, servicio.regalarCoins(1L, 1L, 100));
        assertEquals(Estado.CANTIDAD_INVALIDA, servicio.regalarCoins(1L, 2L, 0));
    }

    @Test
    void regalarItemMueveDelInventario() {
        when(inventario.quitar(1L, "oro", 3)).thenReturn(true);
        assertEquals(Estado.OK, servicio.regalarItem(1L, 2L, "oro", 3));
        verify(inventario).quitar(1L, "oro", 3);
        verify(inventario).anadir(2L, "oro", 3);
    }

    @Test
    void noSeRegalaItemQueNoTienesNiInexistente() {
        when(inventario.quitar(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(false);
        assertEquals(Estado.NO_TIENE, servicio.regalarItem(1L, 2L, "oro", 3));
        assertEquals(Estado.NO_EXISTE, servicio.regalarItem(1L, 2L, "unicornio", 1));
    }
}
