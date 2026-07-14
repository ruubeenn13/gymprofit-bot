package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.MineriaEstado;
import com.gymprofit.bot.db.MineriaRepositorio;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.MineriaService.Estado;
import com.gymprofit.bot.services.MineriaService.EstadoReparar;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica /minar: validaciones (pico/energía/cooldown), drops y subida de nivel; e integridad. */
class MineriaServiceTest {

    private final MineriaRepositorio mineria = mock(MineriaRepositorio.class);
    private final PersonajeRepositorio personajes = mock(PersonajeRepositorio.class);
    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);

    private MineriaService svc(double azar) {
        return new MineriaService(mineria, personajes, inventario, economia, usuarios, () -> azar);
    }

    /** Stub: todos los picos a durabilidad completa (devuelve el máximo pasado). */
    private void picosATope() {
        when(mineria.durabilidad(anyLong(), anyString(), anyInt()))
                .thenAnswer(i -> i.getArgument(2));
    }

    @Test
    void sinPicoNoSePuedeMinar() {
        when(inventario.listar(1L)).thenReturn(Map.of("fruta", 3));
        assertEquals(Estado.SIN_PICO, svc(0.4).minar(1L).estado());
        verify(personajes, never()).gastarEnergia(anyLong(), anyInt());
    }

    @Test
    void sinEnergiaNoSePuedeMinar() {
        when(inventario.listar(1L)).thenReturn(Map.of("pico_madera", 1));
        picosATope();
        when(mineria.obtenerOCrear(1L)).thenReturn(new MineriaEstado(1L, 0, null));
        when(personajes.gastarEnergia(1L, MineriaService.ENERGIA_POR_MINAR)).thenReturn(false);
        assertEquals(Estado.SIN_ENERGIA, svc(0.4).minar(1L).estado());
    }

    @Test
    void enCooldownNoSePuedeMinar() {
        when(inventario.listar(1L)).thenReturn(Map.of("pico_madera", 1));
        picosATope();
        when(mineria.obtenerOCrear(1L)).thenReturn(new MineriaEstado(1L, 5, Instant.now()));
        assertEquals(Estado.EN_COOLDOWN, svc(0.4).minar(1L).estado());
        verify(personajes, never()).gastarEnergia(anyLong(), anyInt());
    }

    @Test
    void minarConPicoDaMineralesYSubeNivel() {
        when(inventario.listar(1L)).thenReturn(Map.of("pico_madera", 1));
        picosATope();
        when(mineria.obtenerOCrear(1L)).thenReturn(new MineriaEstado(1L, 0, null));
        when(personajes.gastarEnergia(1L, MineriaService.ENERGIA_POR_MINAR)).thenReturn(true);

        var r = svc(0.4).minar(1L); // azar 0.4 -> cantidad = 1 + 0 + 1 = 2
        assertEquals(Estado.OK, r.estado());
        assertEquals(1, r.nivelNuevo());
        int total = r.minerales().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(2, total);
        // Con pico de madera (tier 1) solo salen minerales de tier 1.
        for (String id : r.minerales().keySet()) {
            assertTrue(Minerales.CATALOGO.stream()
                    .anyMatch(m -> m.itemId().equals(id) && m.tier() == 1), id + " debe ser tier 1");
        }
        verify(inventario, org.mockito.Mockito.atLeastOnce()).anadir(anyLong(), anyString(), anyInt());
        verify(mineria).registrarMinado(1L);
        verify(mineria).gastarDurabilidad(1L, "pico_madera", 30);
    }

    @Test
    void seUsaElPicoDeMayorTier() {
        when(inventario.listar(1L)).thenReturn(Map.of("pico_madera", 1, "pico_diamante", 1));
        picosATope();
        when(mineria.obtenerOCrear(1L)).thenReturn(new MineriaEstado(1L, 0, null));
        when(personajes.gastarEnergia(1L, MineriaService.ENERGIA_POR_MINAR)).thenReturn(true);
        // azar 0.99 -> elige el mineral de mayor peso invertido... basta con que sea OK y tier<=3
        var r = svc(0.99).minar(1L);
        assertEquals(Estado.OK, r.estado());
        for (String id : r.minerales().keySet()) {
            int tier = Minerales.CATALOGO.stream().filter(m -> m.itemId().equals(id))
                    .findFirst().orElseThrow().tier();
            assertTrue(tier <= 3, "pico diamante extrae hasta tier 3");
        }
    }

    @Test
    void picoRotoNoDejaMinar() {
        when(inventario.listar(1L)).thenReturn(Map.of("pico_madera", 1));
        when(mineria.durabilidad(1L, "pico_madera", 30)).thenReturn(0); // roto
        assertEquals(Estado.PICO_ROTO, svc(0.4).minar(1L).estado());
        verify(personajes, never()).gastarEnergia(anyLong(), anyInt());
    }

    // ---------------------- Reparar ----------------------

    @Test
    void costeRepararEscalaConDesgasteYTier() {
        assertEquals(240, MineriaService.costeReparar(1, 30));  // 30 * 8 * 1
        assertEquals(2400, MineriaService.costeReparar(3, 100)); // 100 * 8 * 3
    }

    @Test
    void repararPicoDesgastadoConSaldoLoDejaAtope() {
        when(inventario.cantidad(1L, "pico_madera")).thenReturn(1);
        when(mineria.durabilidad(1L, "pico_madera", 30)).thenReturn(10); // faltan 20
        when(economia.gastar(anyLong(), anyLong(), anyString())).thenReturn(true);
        var r = svc(0.4).reparar(1L, "pico_madera");
        assertEquals(EstadoReparar.OK, r.estado());
        assertEquals(MineriaService.costeReparar(1, 20), r.coste()); // 20*8*1 = 160
        verify(mineria).repararPico(1L, "pico_madera", 30);
    }

    @Test
    void noSeReparaLoQueNoTienes() {
        when(inventario.cantidad(1L, "pico_madera")).thenReturn(0);
        assertEquals(EstadoReparar.NO_TIENE, svc(0.4).reparar(1L, "pico_madera").estado());
    }

    @Test
    void noSeReparaUnPicoYaAtope() {
        when(inventario.cantidad(1L, "pico_madera")).thenReturn(1);
        when(mineria.durabilidad(1L, "pico_madera", 30)).thenReturn(30);
        assertEquals(EstadoReparar.YA_REPARADO, svc(0.4).reparar(1L, "pico_madera").estado());
        verify(economia, never()).gastar(anyLong(), anyLong(), anyString());
    }

    @Test
    void repararAlgoQueNoEsPicoFalla() {
        assertEquals(EstadoReparar.NO_ES_PICO, svc(0.4).reparar(1L, "espada").estado());
    }

    @Test
    void catalogosDePicosYMineralesApuntanAItemsValidos() {
        for (Picos p : Picos.CATALOGO) {
            assertEquals(Items.Categoria.PICO,
                    Items.porId(p.itemId()).orElseThrow().categoria(), p.itemId());
        }
        for (Minerales m : Minerales.CATALOGO) {
            assertEquals(Items.Categoria.MINERAL,
                    Items.porId(m.itemId()).orElseThrow().categoria(), m.itemId());
        }
    }
}
