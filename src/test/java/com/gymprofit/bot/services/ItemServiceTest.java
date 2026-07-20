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

/**
 * Verifica compra (con/sin saldo), uso de consumibles (aplican efecto) y la <b>saciedad</b> (máximo
 * de consumibles al día) con repos simulados.
 */
class ItemServiceTest {

    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final DescansoService descanso = mock(DescansoService.class);
    private final ItemService servicio =
            new ItemService(economia, inventario, personajes, usuarios, descanso);

    /** Por defecto el jugador tiene hueco: los tests de saciedad lo sobrescriben. */
    ItemServiceTest() {
        when(descanso.puedeConsumir(anyLong())).thenReturn(true);
    }

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
    void usarConsumibleApuntaElConsumoDelDia() {
        when(inventario.quitar(1L, "batido", 1)).thenReturn(true);
        servicio.usar(1L, "batido");
        verify(descanso).registrarConsumo(1L);
    }

    @Test
    void saciedadCortaAlCuartoConsumible() {
        when(descanso.puedeConsumir(1L)).thenReturn(false);

        var r = servicio.usar(1L, "batido");

        assertEquals(EstadoUso.LLENO, r.estado());
        // No se toca el inventario: el intento sale gratis, no se come el ítem.
        verify(inventario, never()).quitar(anyLong(), anyString(), anyInt());
    }

    @Test
    void laSaciedadNoAfectaALoQueNoSeConsume() {
        when(descanso.puedeConsumir(1L)).thenReturn(false);
        // Un ítem de equipo falla por no ser consumible, no por saciedad.
        assertEquals(EstadoUso.NO_CONSUMIBLE, servicio.usar(1L, "coche").estado());
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
