package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.ItemService.EstadoCompra;
import com.gymprofit.bot.services.ItemService.EstadoUso;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica compra (con/sin saldo) y uso de consumibles (aplican efecto) con repos simulados. */
class ItemServiceTest {

    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final ItemService servicio =
            new ItemService(economia, inventario, personajes, usuarios);

    @Test
    void comprarConSaldoAnadeAlInventario() {
        when(economia.gastar(eq(1L), anyLong(), eq("tienda"))).thenReturn(true);
        var r = servicio.comprar(1L, "batido", 2);
        assertEquals(EstadoCompra.OK, r.estado());
        assertEquals(90, r.coste()); // batido 45 × 2
        verify(inventario).anadir(1L, "batido", 2);
    }

    @Test
    void comprarSinSaldoNoAnade() {
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(false);
        var r = servicio.comprar(1L, "batido", 1);
        assertEquals(EstadoCompra.SIN_SALDO, r.estado());
        verify(inventario, never()).anadir(anyLong(), anyString(), anyInt());
    }

    @Test
    void comprarItemInexistente() {
        assertEquals(EstadoCompra.NO_EXISTE, servicio.comprar(1L, "nave_espacial", 1).estado());
    }

    @Test
    void usarConsumibleAplicaEfectoYDescuenta() {
        when(inventario.quitar(1L, "batido", 1)).thenReturn(true);
        var r = servicio.usar(1L, "batido");
        assertEquals(EstadoUso.OK, r.estado());
        verify(personajes).sumarEnergia(1L, 25); // batido = +25 energía
    }

    @Test
    void usarSinTenerlo() {
        when(inventario.quitar(anyLong(), anyString(), anyInt())).thenReturn(false);
        assertEquals(EstadoUso.NO_TIENE, servicio.usar(1L, "batido").estado());
    }

    @Test
    void noSePuedeUsarEquipo() {
        assertEquals(EstadoUso.NO_CONSUMIBLE, servicio.usar(1L, "coche").estado());
    }

    @Test
    void catalogoAmplio() {
        assertTrue(Items.CATALOGO.size() >= 20, "catálogo amplio de ítems");
    }
}
