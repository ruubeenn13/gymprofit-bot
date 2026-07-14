package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.TruequeService.Estado;
import com.gymprofit.bot.services.TruequeService.Oferta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica el intercambio del trueque y el rollback cuando una parte no cumple. */
class TruequeServiceTest {

    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final TruequeService servicio = new TruequeService(economia, inventario, usuarios);

    @Test
    void intercambiaItemDelProponentePorCoinsDelObjetivo() {
        // A da 1 espada; B da 500 coins.
        Oferta o = new Oferta(1L, 2L, "espada", 1, 0, null, 0, 500);
        when(inventario.quitar(1L, "espada", 1)).thenReturn(true);
        when(economia.gastar(eq(2L), eq(500L), anyString())).thenReturn(true);

        assertEquals(Estado.OK, servicio.ejecutar(o));
        verify(inventario).quitar(1L, "espada", 1);
        verify(inventario).anadir(2L, "espada", 1);   // la espada va a B
        verify(economia).gastar(eq(2L), eq(500L), anyString());
        verify(economia).ingresar(eq(1L), eq(500L), anyString()); // los coins van a A
    }

    @Test
    void siElObjetivoNoTieneSuParteSeRevierteLoDelProponente() {
        // A da 1 espada; B debería dar 1 cota de malla pero no la tiene.
        Oferta o = new Oferta(1L, 2L, "espada", 1, 0, "cota_malla", 1, 0);
        when(inventario.quitar(1L, "espada", 1)).thenReturn(true);
        when(inventario.quitar(2L, "cota_malla", 1)).thenReturn(false); // B no tiene

        assertEquals(Estado.OBJETIVO_SIN_ITEM, servicio.ejecutar(o));
        // La espada de A se le devuelve (rollback); nada se entrega a B.
        verify(inventario).anadir(1L, "espada", 1);
        verify(inventario, org.mockito.Mockito.never()).anadir(2L, "espada", 1);
    }

    @Test
    void siElProponenteNoTieneSuItemNoSeCobraAlObjetivo() {
        Oferta o = new Oferta(1L, 2L, "espada", 1, 0, null, 0, 100);
        when(inventario.quitar(1L, "espada", 1)).thenReturn(false);
        assertEquals(Estado.PROPONENTE_SIN_ITEM, servicio.ejecutar(o));
        verify(economia, org.mockito.Mockito.never()).gastar(eq(2L), anyLong(), anyString());
    }
}
