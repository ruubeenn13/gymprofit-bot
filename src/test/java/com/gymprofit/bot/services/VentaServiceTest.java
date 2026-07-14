package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.VentaService.Estado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica /vender: minerales a valor completo, resto a la mitad, y validaciones. */
class VentaServiceTest {

    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final VentaService servicio = new VentaService(inventario, economia, usuarios);

    @Test
    void precioDeVentaMineralCompletoRestoMitad() {
        assertEquals(70, VentaService.precioVenta(Items.porId("oro").orElseThrow()));       // mineral
        assertEquals(1750, VentaService.precioVenta(Items.porId("espada").orElseThrow()));  // 3500/2
    }

    @Test
    void venderMineralPagaValorCompleto() {
        when(inventario.quitar(1L, "oro", 2)).thenReturn(true);
        var r = servicio.vender(1L, "oro", 2);
        assertEquals(Estado.OK, r.estado());
        assertEquals(140, r.total()); // 70 * 2
        verify(economia).ingresar(eq(1L), eq(140L), anyString());
    }

    @Test
    void venderNoMineralPagaLaMitad() {
        when(inventario.quitar(1L, "espada", 1)).thenReturn(true);
        var r = servicio.vender(1L, "espada", 1);
        assertEquals(Estado.OK, r.estado());
        assertEquals(1750, r.total());
    }

    @Test
    void noSePuedeVenderLoQueNoTienes() {
        when(inventario.quitar(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(false);
        assertEquals(Estado.NO_TIENE, servicio.vender(1L, "oro", 5).estado());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }

    @Test
    void cantidadInvalidaFalla() {
        assertEquals(Estado.CANTIDAD_INVALIDA, servicio.vender(1L, "oro", 0).estado());
    }

    @Test
    void itemInexistenteFalla() {
        assertEquals(Estado.NO_EXISTE, servicio.vender(1L, "unicornio", 1).estado());
    }
}
