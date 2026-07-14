package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.ApuestaService.Color;
import com.gymprofit.bot.services.ApuestaService.Estado;
import com.gymprofit.bot.services.ApuestaService.Resultado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica los juegos de azar: cobro, tablas de pago y validaciones (azar inyectado). */
class ApuestaServiceTest {

    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);

    private ApuestaService svc(double azar) {
        return new ApuestaService(economia, usuarios, () -> azar);
    }

    @Test
    void coinflipGanadoPagaElDoble() {
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(true);
        // azar 0.2 -> salió cara; apostamos a cara -> gana
        Resultado r = svc(0.2).coinflip(1L, 100, true);
        assertTrue(r.gano());
        assertEquals(100, r.ganancia()); // premio 200 - apuesta 100
        verify(economia).ingresar(eq(1L), eq(200L), anyString());
    }

    @Test
    void coinflipPerdidoNoPaga() {
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(true);
        // azar 0.8 -> salió cruz; apostamos a cara -> pierde
        Resultado r = svc(0.8).coinflip(1L, 100, true);
        assertFalse(r.gano());
        assertEquals(-100, r.ganancia());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }

    @Test
    void dadoAcertadoPagaCinco() {
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(true);
        // azar 0.0 -> tirada = 1; apostamos al 1 -> gana, premio 500
        Resultado r = svc(0.0).dado(1L, 100, 1);
        assertTrue(r.gano());
        assertEquals(400, r.ganancia());
        verify(economia).ingresar(eq(1L), eq(500L), anyString());
    }

    @Test
    void ruletaVerdePagaTreinta() {
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(true);
        // azar 0.0 -> slot 0 = verde; apostamos verde -> premio 30x
        Resultado r = svc(0.0).ruleta(1L, 100, Color.VERDE);
        assertTrue(r.gano());
        verify(economia).ingresar(eq(1L), eq(3000L), anyString());
    }

    @Test
    void apuestaFueraDeLimiteOsinSaldo() {
        assertEquals(Estado.APUESTA_INVALIDA, svc(0.5).coinflip(1L, 1, true).estado());
        assertEquals(Estado.APUESTA_INVALIDA, svc(0.5).coinflip(1L, 999999, true).estado());
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(false);
        assertEquals(Estado.SIN_SALDO, svc(0.5).coinflip(1L, 100, true).estado());
    }

    @Test
    void dadoConNumeroFueraDeRangoFalla() {
        assertEquals(Estado.APUESTA_INVALIDA, svc(0.5).dado(1L, 100, 7).estado());
    }
}
