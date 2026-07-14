package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.DueloService.Duelo;
import com.gymprofit.bot.services.DueloService.Estado;
import com.gymprofit.bot.services.DueloService.Resultado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica la resolución del duelo (ganador por azar, cobro a ambos y devolución si falla). */
class DueloServiceTest {

    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);

    private DueloService svc(double azar) {
        return new DueloService(economia, usuarios, () -> azar);
    }

    @Test
    void elGanadorSeLlevaAmbasApuestas() {
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(true);
        // azar 0.2 < 0.5 -> gana el retador (1)
        Resultado r = svc(0.2).resolver(new Duelo(1L, 2L, 100));
        assertEquals(Estado.OK, r.estado());
        assertEquals(1L, r.ganador());
        verify(economia).ingresar(eq(1L), eq(200L), anyString());
    }

    @Test
    void ganaElRetadoSiElAzarLoDice() {
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(true);
        Resultado r = svc(0.9).resolver(new Duelo(1L, 2L, 100)); // 0.9 >= 0.5 -> retado (2)
        assertEquals(2L, r.ganador());
        verify(economia).ingresar(eq(2L), eq(200L), anyString());
    }

    @Test
    void siElRetadoNoTieneSaldoSeDevuelveAlRetador() {
        when(economia.gastar(eq(1L), eq(100L), anyString())).thenReturn(true);
        when(economia.gastar(eq(2L), eq(100L), anyString())).thenReturn(false);
        Resultado r = svc(0.2).resolver(new Duelo(1L, 2L, 100));
        assertEquals(Estado.RETADO_SIN_SALDO, r.estado());
        verify(economia).ingresar(eq(1L), eq(100L), anyString()); // devolución al retador
    }
}
