package com.gymprofit.bot.commands.moderacion;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el cálculo de páginas y el estado de los botones del historial (lógica pura). */
class ModlogsComandoTest {

    @Test
    void totalPaginasRedondeaHaciaArriba() {
        assertEquals(1, ModlogsComando.totalPaginas(0));
        assertEquals(1, ModlogsComando.totalPaginas(10));
        assertEquals(2, ModlogsComando.totalPaginas(11));
        assertEquals(3, ModlogsComando.totalPaginas(25));
    }

    @Test
    void sinBotonesSiCabeEnUnaPagina() {
        assertTrue(ModlogsComando.construirBotones(1L, 0, 0).isEmpty());
        assertTrue(ModlogsComando.construirBotones(1L, 0, 10).isEmpty());
    }

    @Test
    void primeraPaginaDeshabilitaAnterior() {
        List<Button> botones = ModlogsComando.construirBotones(1L, 0, 25).get(0).getButtons();
        assertTrue(botones.get(0).isDisabled(), "◀ deshabilitado en la primera página");
        assertFalse(botones.get(1).isDisabled(), "▶ habilitado si hay más páginas");
    }

    @Test
    void ultimaPaginaDeshabilitaSiguiente() {
        ActionRow fila = ModlogsComando.construirBotones(1L, 2, 25).get(0); // 25 -> 3 páginas (0..2)
        List<Button> botones = fila.getButtons();
        assertFalse(botones.get(0).isDisabled(), "◀ habilitado fuera de la primera página");
        assertTrue(botones.get(1).isDisabled(), "▶ deshabilitado en la última página");
    }
}
