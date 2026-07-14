package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.ListadoMercado;
import com.gymprofit.bot.db.MercadoRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.MercadoService.CompraEstado;
import com.gymprofit.bot.services.MercadoService.PublicarEstado;
import com.gymprofit.bot.services.MercadoService.RetirarEstado;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica el mercado: publicar (escrow), comprar (comisión, reserva/rollback) y retirar. */
class MercadoServiceTest {

    private final MercadoRepositorio mercado = mock(MercadoRepositorio.class);
    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final MercadoService servicio =
            new MercadoService(mercado, inventario, economia, usuarios);

    private static ListadoMercado anuncio(long vendedor, int cantidad) {
        return new ListadoMercado(5L, vendedor, "oro", cantidad, 50);
    }

    @Test
    void publicarRetiraDelInventarioYCreaAnuncio() {
        when(inventario.quitar(1L, "oro", 10)).thenReturn(true);
        when(mercado.crear(1L, "oro", 10, 50)).thenReturn(5L);
        var r = servicio.publicar(1L, "oro", 10, 50);
        assertEquals(PublicarEstado.OK, r.estado());
        assertEquals(5L, r.id());
        verify(inventario).quitar(1L, "oro", 10);
    }

    @Test
    void noSePublicaLoQueNoTienes() {
        when(inventario.quitar(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(false);
        assertEquals(PublicarEstado.NO_TIENE, servicio.publicar(1L, "oro", 10, 50).estado());
        verify(mercado, never()).crear(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyInt(), anyLong());
    }

    @Test
    void comprarCobraAlCompradorYPagaAlVendedorConComision() {
        when(mercado.buscar(5L)).thenReturn(Optional.of(anuncio(2L, 10)));
        when(mercado.reservar(5L, 3)).thenReturn(true);
        when(economia.gastar(eq(1L), eq(150L), anyString())).thenReturn(true);
        var r = servicio.comprar(1L, 5L, 3);
        assertEquals(CompraEstado.OK, r.estado());
        assertEquals(150L, r.total());
        assertEquals(8L, r.comision()); // round(150 * 0.05)
        verify(economia).gastar(eq(1L), eq(150L), anyString());
        verify(economia).ingresar(eq(2L), eq(142L), anyString()); // 150 - 8
        verify(inventario).anadir(1L, "oro", 3);
    }

    @Test
    void noSePuedeComprarTuPropioAnuncio() {
        when(mercado.buscar(5L)).thenReturn(Optional.of(anuncio(1L, 10)));
        assertEquals(CompraEstado.ES_TUYO, servicio.comprar(1L, 5L, 1).estado());
    }

    @Test
    void siElCobroFallaSeRevierteLaReserva() {
        when(mercado.buscar(5L)).thenReturn(Optional.of(anuncio(2L, 10)));
        when(mercado.reservar(5L, 3)).thenReturn(true);
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(false);
        assertEquals(CompraEstado.SIN_SALDO, servicio.comprar(1L, 5L, 3).estado());
        verify(mercado).devolver(5L, 3);
        verify(inventario, never()).anadir(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void comprarMasDeLoDisponibleFalla() {
        when(mercado.buscar(5L)).thenReturn(Optional.of(anuncio(2L, 2)));
        assertEquals(CompraEstado.SIN_STOCK, servicio.comprar(1L, 5L, 5).estado());
    }

    @Test
    void comprarAnuncioInexistenteFalla() {
        when(mercado.buscar(5L)).thenReturn(Optional.empty());
        assertEquals(CompraEstado.NO_EXISTE, servicio.comprar(1L, 5L, 1).estado());
    }

    @Test
    void retirarDevuelveLosItemsAlVendedor() {
        when(mercado.buscar(5L)).thenReturn(Optional.of(anuncio(1L, 7)));
        assertEquals(RetirarEstado.OK, servicio.retirar(1L, 5L));
        verify(inventario).anadir(1L, "oro", 7);
        verify(mercado).eliminar(5L);
    }

    @Test
    void noSePuedeRetirarUnAnuncioAjeno() {
        when(mercado.buscar(5L)).thenReturn(Optional.of(anuncio(2L, 7)));
        assertEquals(RetirarEstado.NO_TUYO, servicio.retirar(1L, 5L));
        verify(mercado, never()).eliminar(anyLong());
    }
}
