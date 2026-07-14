package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.MisionRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica el avance y la compleción automática de misiones al vencer en combate. */
class MisionServiceTest {

    private final MisionRepositorio repo = mock(MisionRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final XpService xp = mock(XpService.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final MisionService servicio = new MisionService(repo, economia, xp, usuarios);

    private static Monstruos m(String id) {
        return Monstruos.porId(id).orElseThrow();
    }

    @Test
    void matarUnMonstruoAvanzaLasMisionesQueCasan() {
        when(repo.progreso(1L)).thenReturn(Map.of());
        List<Misiones> comp = servicio.registrarVictoria(1L, m("lobo")); // lobo, mundo bosque
        assertTrue(comp.isEmpty());
        verify(repo).fijarProgreso(1L, "cazador_lobos", 1);  // MONSTRUO lobo
        verify(repo).fijarProgreso(1L, "limpieza_bosque", 1); // MUNDO bosque
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }

    @Test
    void alLlegarALaMetaLaMisionSeCompletaYPaga() {
        when(repo.progreso(1L)).thenReturn(Map.of("cazador_lobos", 9)); // meta 10
        List<Misiones> comp = servicio.registrarVictoria(1L, m("lobo"));
        assertTrue(comp.stream().anyMatch(x -> x.id().equals("cazador_lobos")));
        verify(economia).ingresar(eq(1L), eq(150L), anyString()); // recompensa cazador_lobos
        verify(xp).ganarXp(1L, 60);
        verify(repo).fijarProgreso(1L, "cazador_lobos", 0); // reinicia (repetible)
    }

    @Test
    void matarUnJefeCuentaParaMatajefes() {
        when(repo.progreso(1L)).thenReturn(Map.of("matajefes", 2)); // meta 3
        List<Misiones> comp = servicio.registrarVictoria(1L, m("ent_ancestral")); // jefe del bosque
        assertTrue(comp.stream().anyMatch(x -> x.id().equals("matajefes")));
        verify(economia).ingresar(eq(1L), eq(800L), anyString());
        verify(repo).fijarProgreso(1L, "matajefes", 0);
    }

    @Test
    void listarDevuelveTodoElCatalogoConProgreso() {
        when(repo.progreso(1L)).thenReturn(Map.of("cazador_lobos", 4));
        List<MisionService.Vista> vistas = servicio.listar(1L);
        assertEquals(Misiones.CATALOGO.size(), vistas.size());
        assertTrue(vistas.stream().anyMatch(v ->
                v.mision().id().equals("cazador_lobos") && v.progreso() == 4));
    }
}
