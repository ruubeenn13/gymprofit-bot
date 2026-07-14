package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.RoboService.Estado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica el robo: éxito (botín con tope), fallo (multa) y víctima sin saldo. */
class RoboServiceTest {

    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);

    private RoboService svc(double azar) {
        return new RoboService(economia, usuarios, () -> azar);
    }

    @Test
    void robarConExitoTransfiereElBotin() {
        when(economia.saldo(2L)).thenReturn(1000L); // 15% = 150
        var r = svc(0.1).robar(1L, 2L); // azar 0.1 < 0.5 -> éxito
        assertEquals(Estado.EXITO, r.estado());
        assertEquals(150, r.cantidad());
        verify(economia).gastar(eq(2L), eq(150L), anyString());
        verify(economia).ingresar(eq(1L), eq(150L), anyString());
    }

    @Test
    void elBotinTieneTope() {
        when(economia.saldo(2L)).thenReturn(1_000_000L); // 15% enorme -> tope 2000
        var r = svc(0.1).robar(1L, 2L);
        assertEquals(2000, r.cantidad());
    }

    @Test
    void alFallarSePagaMultaALaVictima() {
        when(economia.saldo(2L)).thenReturn(1000L);
        when(economia.gastar(eq(1L), eq(RoboService.MULTA), anyString())).thenReturn(true);
        var r = svc(0.9).robar(1L, 2L); // azar 0.9 >= 0.5 -> falla
        assertEquals(Estado.FALLO, r.estado());
        assertEquals(RoboService.MULTA, r.cantidad());
        verify(economia).ingresar(eq(2L), eq(RoboService.MULTA), anyString());
    }

    @Test
    void noSeRobaAQuienNoTieneCoins() {
        when(economia.saldo(2L)).thenReturn(0L);
        assertEquals(Estado.VICTIMA_SIN_SALDO, svc(0.1).robar(1L, 2L).estado());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }
}
