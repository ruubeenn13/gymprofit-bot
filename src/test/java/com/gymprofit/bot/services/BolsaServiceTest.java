package com.gymprofit.bot.services;

import com.gymprofit.bot.db.BolsaRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Posicion;
import com.gymprofit.bot.db.PrecioAccion;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.BolsaService.CarteraVista;
import com.gymprofit.bot.services.BolsaService.EstadoInvertir;
import com.gymprofit.bot.services.BolsaService.EstadoVender;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica compra/venta de acciones (comisión, P/L) y el movimiento de precios del tick. */
class BolsaServiceTest {

    private final BolsaRepositorio bolsa = mock(BolsaRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);

    private BolsaService svc(double azar) {
        return new BolsaService(bolsa, economia, usuarios, () -> azar);
    }

    @Test
    void randomWalkAcotadoYConSuelo() {
        assertEquals(100, Acciones.mover(100, 0.10, 0.5));  // factor 1.0
        assertEquals(110, Acciones.mover(100, 0.10, 1.0));  // +10 %
        assertEquals(90, Acciones.mover(100, 0.10, 0.0));   // -10 %
        assertEquals(1, Acciones.mover(1, 0.5, 0.0));       // suelo en 1
    }

    @Test
    void invertirCobraPrecioMasComision() {
        when(bolsa.precio("gymx")).thenReturn(Optional.of(new PrecioAccion("gymx", 200, 200)));
        when(economia.gastar(eq(1L), eq(606L), anyString())).thenReturn(true);
        var r = svc(0.5).invertir(1L, "gymx", 3); // 600 + 1% = 606
        assertEquals(EstadoInvertir.OK, r.estado());
        assertEquals(606, r.coste());
        verify(bolsa).comprar(1L, "gymx", 3, 600);
    }

    @Test
    void invertirSinSaldoNoCompra() {
        when(bolsa.precio("gymx")).thenReturn(Optional.of(new PrecioAccion("gymx", 200, 200)));
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(false);
        assertEquals(EstadoInvertir.SIN_SALDO, svc(0.5).invertir(1L, "gymx", 3).estado());
    }

    @Test
    void venderIngresaNetoYReduceLaPosicion() {
        when(bolsa.precio("gymx")).thenReturn(Optional.of(new PrecioAccion("gymx", 200, 200)));
        when(bolsa.posicion(1L, "gymx")).thenReturn(Optional.of(new Posicion("gymx", 5, 900)));
        var r = svc(0.5).vender(1L, "gymx", 2); // bruto 400, -1% = 396
        assertEquals(EstadoVender.OK, r.estado());
        assertEquals(396, r.neto());
        verify(economia).ingresar(eq(1L), eq(396L), anyString());
        verify(bolsa).fijarPosicion(1L, "gymx", 3, 540); // coste 900 * 3/5
    }

    @Test
    void venderSinAccionesFalla() {
        when(bolsa.precio("gymx")).thenReturn(Optional.of(new PrecioAccion("gymx", 200, 200)));
        when(bolsa.posicion(1L, "gymx")).thenReturn(Optional.empty());
        assertEquals(EstadoVender.SIN_ACCIONES, svc(0.5).vender(1L, "gymx", 1).estado());
    }

    @Test
    void carteraValoraAPrecioActualYCalculaPL() {
        when(bolsa.cartera(1L)).thenReturn(List.of(new Posicion("gymx", 5, 900)));
        when(bolsa.precio("gymx")).thenReturn(Optional.of(new PrecioAccion("gymx", 200, 200)));
        CarteraVista c = svc(0.5).cartera(1L);
        assertEquals(1000, c.valorTotal()); // 200 * 5
        assertEquals(100, c.plTotal());     // 1000 - 900
    }

    @Test
    void elTickMueveLosPrecios() {
        when(bolsa.precios()).thenReturn(List.of(new PrecioAccion("gymx", 200, 200)));
        // azar 0.9: no hay evento (0.9 >= 0.06); mover(200, 0.05, 0.9) = 200 * 1.04 = 208
        svc(0.9).tick();
        verify(bolsa).actualizarPrecio("gymx", 208);
    }
}
