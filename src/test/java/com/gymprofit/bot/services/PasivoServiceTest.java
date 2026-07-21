package com.gymprofit.bot.services;

import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.PasivoRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.PasivoService.Estado;
import com.gymprofit.bot.services.PasivoService.EstadoRanura;
import com.gymprofit.bot.services.PasivoService.ResultadoEquipar;
import com.gymprofit.bot.services.PasivoService.ResultadoQuitar;
import com.gymprofit.bot.services.Pasivos.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica {@link PasivoService} con los repositorios mockeados: desbloqueo de ranuras por nivel,
 * filtrado contra el inventario (el anti-exploit), saturación por tipo y los ocho estados de error
 * de la tabla del diseño.
 */
class PasivoServiceTest {

    private final PasivoRepositorio repo = mock(PasivoRepositorio.class);
    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);

    private final PasivoService svc = new PasivoService(repo, inventario, usuarios);

    /** Fija el nivel del jugador 1 (y por tanto sus ranuras disponibles). */
    private void nivel(int nivel) {
        when(usuarios.buscar(1L)).thenReturn(Optional.of(
                new UsuarioDiscord(1L, 0, nivel, 0, 0, null, "es", false)));
    }

    @Test
    @DisplayName("8 · ranurasDe: los bordes exactos 0/10/25/50, atados a Rango")
    void ranurasPorNivel() {
        assertEquals(1, PasivoService.ranurasDe(0));
        assertEquals(1, PasivoService.ranurasDe(9));
        assertEquals(2, PasivoService.ranurasDe(10));
        assertEquals(2, PasivoService.ranurasDe(24));
        assertEquals(3, PasivoService.ranurasDe(25));
        assertEquals(3, PasivoService.ranurasDe(49));
        assertEquals(4, PasivoService.ranurasDe(50));
        assertEquals(4, PasivoService.ranurasDe(100));
        assertEquals(0, PasivoService.nivelDeRanura(1));
        assertEquals(10, PasivoService.nivelDeRanura(2));
        assertEquals(25, PasivoService.nivelDeRanura(3));
        assertEquals(50, PasivoService.nivelDeRanura(4));
        // Los umbrales NO se copian a mano: salen de Rango. Si alguien toca el enum (o añade un rango
        // nuevo) este test cae y obliga a decidir a conciencia qué pasa con las ranuras.
        assertEquals(Rango.values().length, PasivoService.RANURAS_MAX, "una ranura por rango");
        for (int r = 1; r <= PasivoService.RANURAS_MAX; r++) {
            assertEquals(Rango.values()[r - 1].nivelMin(), PasivoService.nivelDeRanura(r),
                    "la ranura " + r + " se abre con su rango");
        }
        assertEquals(Integer.MAX_VALUE, PasivoService.nivelDeRanura(5), "la ranura 5 no existe");
    }

    @Test
    @DisplayName("9 · el bono solo cuenta si el ítem sigue en el inventario (anti-exploit)")
    void filtradoPorInventario() {
        nivel(50);
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet", 2, "yate"));
        // Vendió el yate: la fila sigue ahí, pero deja de contar.
        when(inventario.listar(1L)).thenReturn(Map.of("jet", 1));

        Map<Tipo, Double> bonos = svc.bonosDe(1L);
        assertEquals(0.11, bonos.get(Tipo.SUELDO), 1e-9, "solo el jet: 11 %, no 18 %");
        assertEquals(0.06, bonos.get(Tipo.XP), 1e-9, "solo el jet: 6 %, no 13 %");
        assertEquals(0.0, bonos.get(Tipo.ENERGIA_REGEN), 1e-9, "la energía la daba el yate");
    }

    @Test
    @DisplayName("9b · una ranura por encima del nivel del jugador tampoco cuenta")
    void ranuraBloqueadaNoCuenta() {
        nivel(0); // solo 1 ranura
        when(repo.equipados(1L)).thenReturn(Map.of(1, "gorra", 2, "jet"));
        when(inventario.listar(1L)).thenReturn(Map.of("gorra", 1, "jet", 1));

        assertEquals(0.02, svc.bonoDe(1L, Tipo.XP), 1e-9, "solo la gorra: la ranura 2 no está abierta");
        assertEquals(0.0, svc.bonoDe(1L, Tipo.SUELDO), 1e-9);
    }

    @Test
    @DisplayName("10 · saturación por tipo: 35 % de sueldo se queda en 30 %")
    void saturacionPorTipo() {
        Map<Tipo, Double> bonos = PasivoService.sumarYTopar(List.of(
                Pasivos.porId("jet").orElseThrow(),        // 11 %
                Pasivos.porId("avioneta").orElseThrow(),   //  9 %
                Pasivos.porId("traje").orElseThrow(),      //  8 %
                Pasivos.porId("coche_lujo").orElseThrow()));//  7 % → 35 %
        assertEquals(0.30, bonos.get(Tipo.SUELDO), 1e-9, "el tope satura, no da error");
    }

    @Test
    @DisplayName("11 · se topa la SUMA, no cada bono por separado")
    void seTopaLaSuma() {
        // yate 7 % + jet 6 % + avioneta 6 % + reloj 5 % = 24 % de XP, tope 20 %.
        Map<Tipo, Double> bonos = PasivoService.sumarYTopar(List.of(
                Pasivos.porId("yate").orElseThrow(),
                Pasivos.porId("jet").orElseThrow(),
                Pasivos.porId("avioneta").orElseThrow(),
                Pasivos.porId("reloj").orElseThrow()));
        assertEquals(0.20, bonos.get(Tipo.XP), 1e-9, "20 %, ni 24 % ni el máximo individual (7 %)");
    }

    @Test
    @DisplayName("12 · un tipo sin fuentes devuelve 0.0, no null ni ausencia")
    void tipoSinFuentesDevuelveCero() {
        Map<Tipo, Double> vacio = PasivoService.sumarYTopar(List.of());
        assertEquals(Tipo.values().length, vacio.size(), "siempre los nueve tipos");
        for (Tipo t : Tipo.values()) {
            assertEquals(0.0, vacio.get(t), 1e-9, "el tipo " + t + " debe venir a 0.0");
        }
    }

    @Test
    @DisplayName("13a · error NO_EXISTE: el ítem no está en el catálogo de Items")
    void errorNoExiste() {
        assertEquals(Estado.NO_EXISTE, svc.equipar(1L, "no_existe", null).estado());
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("13b · error SIN_PASIVO: el ítem existe pero no da nada (consumibles, camas…)")
    void errorSinPasivo() {
        assertEquals(Estado.SIN_PASIVO, svc.equipar(1L, "fruta", null).estado());
        assertEquals(Estado.SIN_PASIVO, svc.equipar(1L, "casa", null).estado(),
                "una cama no da pasivo: su efecto ya lo da Camas");
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("13c · error NO_TIENE: no está en el inventario")
    void errorNoTiene() {
        when(inventario.cantidad(1L, "jet")).thenReturn(0);
        assertEquals(Estado.NO_TIENE, svc.equipar(1L, "jet", null).estado());
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("13d · error RANURA_INVALIDA: fuera de 1..4")
    void errorRanuraInvalida() {
        when(inventario.cantidad(1L, "gorra")).thenReturn(1);
        when(repo.equipados(1L)).thenReturn(Map.of());
        nivel(50);
        assertEquals(Estado.RANURA_INVALIDA, svc.equipar(1L, "gorra", 0).estado());
        assertEquals(Estado.RANURA_INVALIDA, svc.equipar(1L, "gorra", 5).estado());
        assertEquals(Estado.RANURA_INVALIDA, svc.quitar(1L, 9).estado());
    }

    @Test
    @DisplayName("13e · error RANURA_BLOQUEADA: informa del nivel que hace falta")
    void errorRanuraBloqueada() {
        when(inventario.cantidad(1L, "gorra")).thenReturn(1);
        when(repo.equipados(1L)).thenReturn(Map.of());
        nivel(18);
        ResultadoEquipar r = svc.equipar(1L, "gorra", 3);
        assertEquals(Estado.RANURA_BLOQUEADA, r.estado());
        assertEquals(25, r.nivelRequerido(), "la ranura 3 se abre en el nivel 25");
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("13f · error YA_EQUIPADO: el mismo ítem no puede ocupar dos ranuras")
    void errorYaEquipado() {
        when(inventario.cantidad(1L, "jet")).thenReturn(1);
        when(repo.equipados(1L)).thenReturn(Map.of(2, "jet"));
        nivel(50);
        ResultadoEquipar r = svc.equipar(1L, "jet", 3);
        assertEquals(Estado.YA_EQUIPADO, r.estado());
        assertEquals(2, r.ranura(), "hay que decirle en qué ranura lo tiene");
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("13g · error VACIA: quitar de una ranura que ya está vacía")
    void errorVacia() {
        when(repo.equipados(1L)).thenReturn(Map.of());
        assertEquals(Estado.VACIA, svc.quitar(1L, 2).estado());
        verify(repo, never()).quitar(anyLong(), anyInt());
    }

    @Test
    @DisplayName("14a · equipar sin ranura usa la primera libre DESBLOQUEADA")
    void equiparSinRanuraUsaLaPrimeraLibre() {
        when(inventario.cantidad(1L, "gorra")).thenReturn(1);
        when(inventario.listar(1L)).thenReturn(Map.of("gorra", 1, "jet", 1));
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet"));
        nivel(25); // 3 ranuras

        ResultadoEquipar r = svc.equipar(1L, "gorra", null);
        assertEquals(Estado.OK, r.estado());
        assertEquals(2, r.ranura(), "la 1 está ocupada; la 2 está libre y abierta");
        assertNull(r.reemplazado(), "no ha pisado nada");
        verify(repo).equipar(1L, 2, "gorra");
    }

    @Test
    @DisplayName("14b · equipar sin ranura y sin huecos devuelve SIN_HUECO sin tocar nada")
    void equiparSinHueco() {
        when(inventario.cantidad(1L, "gorra")).thenReturn(1);
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet", 2, "yate", 3, "dron", 4, "cohete"));
        nivel(50);

        assertEquals(Estado.SIN_HUECO, svc.equipar(1L, "gorra", null).estado());
        verify(repo, never()).equipar(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("14c · equipar sobre ranura ocupada reemplaza y dice qué ha salido")
    void equiparReemplaza() {
        when(inventario.cantidad(1L, "gorra")).thenReturn(1);
        when(inventario.listar(1L)).thenReturn(Map.of("gorra", 1));
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet"));
        nivel(50);

        ResultadoEquipar r = svc.equipar(1L, "gorra", 1);
        assertEquals(Estado.OK, r.estado());
        assertEquals("jet", r.reemplazado(), "pisar un jet no puede ser silencioso");
        verify(repo).equipar(1L, 1, "gorra");
    }

    @Test
    @DisplayName("15 · quitar devuelve el ítem que sale y los totales resultantes")
    void quitarOk() {
        when(repo.equipados(1L)).thenReturn(Map.of(2, "gorra"));
        when(inventario.listar(1L)).thenReturn(Map.of("gorra", 1));
        nivel(50);

        ResultadoQuitar r = svc.quitar(1L, 2);
        assertEquals(Estado.OK, r.estado());
        assertEquals("gorra", r.itemId());
        verify(repo).quitar(1L, 2);
    }

    @Test
    @DisplayName("16 · ranuras() devuelve siempre 4, marcando bloqueadas y ausentes")
    void vistaDeRanuras() {
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet", 2, "yate"));
        when(inventario.listar(1L)).thenReturn(Map.of("jet", 1)); // el yate ya no lo tiene
        nivel(10); // 2 ranuras

        List<EstadoRanura> rs = svc.ranuras(1L);
        assertEquals(4, rs.size());
        assertEquals("jet", rs.get(0).itemId());
        assertFalse(rs.get(0).bloqueada());
        assertFalse(rs.get(0).falta());
        assertEquals("yate", rs.get(1).itemId());
        assertTrue(rs.get(1).falta(), "equipado pero ausente del inventario: no cuenta");
        assertTrue(rs.get(2).bloqueada(), "la 3 pide nivel 25");
        assertTrue(rs.get(3).bloqueada(), "la 4 pide nivel 50");
        assertNull(rs.get(2).itemId());
    }

    @Test
    @DisplayName("17 · equipablesDe: lo que posees, tiene pasivo y no está ya equipado")
    void equipables() {
        when(inventario.listar(1L)).thenReturn(Map.of(
                "jet", 1, "gorra", 1, "fruta", 5, "casa", 1, "yate", 1));
        when(repo.equipados(1L)).thenReturn(Map.of(1, "jet"));

        List<String> res = svc.equipablesDe(1L);
        assertEquals(List.of("gorra", "yate"), res,
                "fruta no tiene pasivo, casa es cama y el jet ya está equipado; orden del catálogo");
    }
}
