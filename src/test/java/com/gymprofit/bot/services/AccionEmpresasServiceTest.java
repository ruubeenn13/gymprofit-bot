package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaAccionRepositorio;
import com.gymprofit.bot.db.EmpresaAccionRepositorio.PosicionAccion;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.MiembroEmpresa;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.AccionEmpresasService.EstadoCompra;
import com.gymprofit.bot.services.AccionEmpresasService.EstadoVenta;
import com.gymprofit.bot.services.AccionEmpresasService.PosicionVista;
import com.gymprofit.bot.services.AccionEmpresasService.ResultadoCompra;
import com.gymprofit.bot.services.AccionEmpresasService.ResultadoVenta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica comprar/vender/cartera de participaciones: precio derivado del prestigio, patrón atómico
 * (validar → gate del dinero → registrar) y que un gate en falso no toca el bote ni las participaciones.
 * El precio se recalcula en el test desde {@link Prestigio}/{@link Accion} en vez de fijarlo a mano.
 */
class AccionEmpresasServiceTest {

    private static final long EMP = 7L;
    private static final long ACTOR = 1L;
    private static final long OTHER = 2L;
    private static final int NIVEL = 2;
    private static final long BOTE = 500_000L;
    private static final int MIEMBROS = 3;

    private final EmpresaRepositorio repo = mock(EmpresaRepositorio.class);
    private final EmpresaAccionRepositorio accRepo = mock(EmpresaAccionRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);

    private final AccionEmpresasService service =
            new AccionEmpresasService(repo, accRepo, economia, usuarios);

    /** Empresa con prestigio conocido → precio determinista = prestigio / 100 (suelo 1). */
    private Empresa empresa() {
        return empresa(BOTE);
    }

    /** Empresa con el bote indicado (para los tests de dividendos, donde el bote es lo que importa). */
    private Empresa empresa(long bote) {
        return new Empresa(EMP, "FUERZA", 99L, "ACME", NIVEL, bote,
                Instant.now(), null, 0L, 0, false, 0L, 0L);
    }

    /** Precio esperado, calculado con las mismas piezas puras que usa el service. */
    private long precioEsperado() {
        return Accion.precioParticipacion(Prestigio.calcular(NIVEL, MIEMBROS, BOTE));
    }

    /** Lista de tamaño fijo para {@code repo.miembros}: solo cuenta su {@code size()}. */
    private List<MiembroEmpresa> miembros() {
        return Arrays.asList(new MiembroEmpresa[MIEMBROS]);
    }

    @Test
    void comprarOkCobraAlJugadorYLlenaElBote() {
        long precio = precioEsperado();
        long coste = precio * 2;
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMP)).thenReturn(miembros());
        when(accRepo.vendidasDe(EMP)).thenReturn(10);
        when(accRepo.participacionesDe(EMP, ACTOR)).thenReturn(5);
        when(economia.gastar(eq(ACTOR), eq(coste), eq("acciones_comprar:" + EMP))).thenReturn(true);

        ResultadoCompra r = service.comprar(ACTOR, EMP, 2);

        assertEquals(EstadoCompra.OK, r.estado());
        assertEquals(2, r.cantidad());
        assertEquals(precio, r.precio());
        assertEquals(coste, r.coste());
        verify(usuarios).obtenerOCrear(ACTOR);
        verify(economia).gastar(ACTOR, coste, "acciones_comprar:" + EMP);
        verify(repo).incrementarBote(EMP, coste);
        verify(accRepo).fijar(EMP, ACTOR, 7); // 5 previas + 2
    }

    @Test
    void comprarSinParticipacionesLibresFalla() {
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa()));
        when(accRepo.vendidasDe(EMP)).thenReturn(99); // libres = 1 < 2

        ResultadoCompra r = service.comprar(ACTOR, EMP, 2);

        assertEquals(EstadoCompra.SIN_PARTICIPACIONES_LIBRES, r.estado());
        verify(economia, never()).gastar(anyLong(), anyLong(), anyString());
        verify(accRepo, never()).fijar(anyLong(), anyLong(), anyInt());
    }

    @Test
    void comprarSinSaldoNoTocaBoteNiParticipaciones() {
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMP)).thenReturn(miembros());
        when(accRepo.vendidasDe(EMP)).thenReturn(0);
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(false);

        ResultadoCompra r = service.comprar(ACTOR, EMP, 2);

        assertEquals(EstadoCompra.SIN_SALDO, r.estado());
        verify(repo, never()).incrementarBote(anyLong(), anyLong());
        verify(accRepo, never()).fijar(anyLong(), anyLong(), anyInt());
    }

    @Test
    void comprarCantidadInvalidaFalla() {
        assertEquals(EstadoCompra.CANTIDAD_INVALIDA, service.comprar(ACTOR, EMP, 0).estado());
    }

    @Test
    void comprarEmpresaInexistenteFalla() {
        when(repo.porId(EMP)).thenReturn(Optional.empty());
        assertEquals(EstadoCompra.NO_EXISTE, service.comprar(ACTOR, EMP, 2).estado());
    }

    @Test
    void venderOkPagaDelBoteAlJugador() {
        long precio = precioEsperado();
        long valor = precio * 2;
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMP)).thenReturn(miembros());
        when(accRepo.participacionesDe(EMP, ACTOR)).thenReturn(5);
        when(repo.gastarDelBote(EMP, valor)).thenReturn(true);

        ResultadoVenta r = service.vender(ACTOR, EMP, 2);

        assertEquals(EstadoVenta.OK, r.estado());
        assertEquals(2, r.cantidad());
        assertEquals(precio, r.precio());
        assertEquals(valor, r.valor());
        verify(repo).gastarDelBote(EMP, valor);
        verify(usuarios).obtenerOCrear(ACTOR);
        verify(economia).ingresar(ACTOR, valor, "acciones_vender:" + EMP);
        verify(accRepo).fijar(EMP, ACTOR, 3); // 5 previas - 2
    }

    @Test
    void venderSinParticipacionesFalla() {
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa()));
        when(accRepo.participacionesDe(EMP, ACTOR)).thenReturn(1); // < 2

        ResultadoVenta r = service.vender(ACTOR, EMP, 2);

        assertEquals(EstadoVenta.SIN_PARTICIPACIONES, r.estado());
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
    }

    @Test
    void venderEmpresaSinFondosNoIngresaNiFija() {
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMP)).thenReturn(miembros());
        when(accRepo.participacionesDe(EMP, ACTOR)).thenReturn(5);
        when(repo.gastarDelBote(anyLong(), anyLong())).thenReturn(false);

        ResultadoVenta r = service.vender(ACTOR, EMP, 2);

        assertEquals(EstadoVenta.EMPRESA_SIN_FONDOS, r.estado());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
        verify(accRepo, never()).fijar(anyLong(), anyLong(), anyInt());
    }

    @Test
    void venderCantidadInvalidaFalla() {
        assertEquals(EstadoVenta.CANTIDAD_INVALIDA, service.vender(ACTOR, EMP, 0).estado());
    }

    @Test
    void carteraValoraCadaPosicionAPrecioActual() {
        long precio = precioEsperado();
        when(accRepo.carteraDe(ACTOR)).thenReturn(List.of(new PosicionAccion(EMP, 4)));
        when(repo.porId(EMP)).thenReturn(Optional.of(empresa()));
        when(repo.miembros(EMP)).thenReturn(miembros());

        List<PosicionVista> cartera = service.cartera(ACTOR);

        assertEquals(1, cartera.size());
        assertEquals("ACME", cartera.get(0).empresa());
        assertEquals(4, cartera.get(0).cantidad());
        assertEquals(precio, cartera.get(0).precio());
        assertEquals(precio * 4, cartera.get(0).valor());
    }

    @Test
    @DisplayName("reparte el pot proporcional; la parte no vendida se queda; gate único")
    void repartirProporcional() {
        // bote 100.000 → pot = floor(0.05*100000) = 5.000
        Empresa e = empresa(100_000L);
        when(accRepo.accionistas(EMP)).thenReturn(List.of(
                new EmpresaAccionRepositorio.Accionista(ACTOR, 25),
                new EmpresaAccionRepositorio.Accionista(OTHER, 15)));
        when(repo.gastarDelBote(EMP, 2_000L)).thenReturn(true); // 1250 + 750
        AccionEmpresasService.ResultadoDividendo r = service.repartirDividendos(e);
        assertEquals(AccionEmpresasService.EstadoDividendo.PAGADO, r.estado());
        assertEquals(2_000L, r.total());
        verify(repo).gastarDelBote(EMP, 2_000L);
        verify(economia).ingresar(ACTOR, 1_250L, "dividendo:" + EMP);
        verify(economia).ingresar(OTHER, 750L, "dividendo:" + EMP);
    }

    @Test
    @DisplayName("sin accionistas → NADA, no toca el bote")
    void repartirSinAccionistas() {
        when(accRepo.accionistas(EMP)).thenReturn(List.of());
        AccionEmpresasService.ResultadoDividendo r = service.repartirDividendos(empresa(100_000L));
        assertEquals(AccionEmpresasService.EstadoDividendo.NADA, r.estado());
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
    }

    @Test
    @DisplayName("bote 0 → NADA")
    void repartirBoteCero() {
        when(accRepo.accionistas(EMP)).thenReturn(List.of(new EmpresaAccionRepositorio.Accionista(ACTOR, 25)));
        AccionEmpresasService.ResultadoDividendo r = service.repartirDividendos(empresa(0L));
        assertEquals(AccionEmpresasService.EstadoDividendo.NADA, r.estado());
        verify(repo, never()).gastarDelBote(anyLong(), anyLong());
    }

    @Test
    @DisplayName("carrera: el bote bajó entre calcular y cobrar → NADA, no paga a nadie")
    void repartirCarrera() {
        Empresa e = empresa(100_000L);
        when(accRepo.accionistas(EMP)).thenReturn(List.of(new EmpresaAccionRepositorio.Accionista(ACTOR, 25)));
        when(repo.gastarDelBote(eq(EMP), anyLong())).thenReturn(false);
        AccionEmpresasService.ResultadoDividendo r = service.repartirDividendos(e);
        assertEquals(AccionEmpresasService.EstadoDividendo.NADA, r.estado());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }
}
