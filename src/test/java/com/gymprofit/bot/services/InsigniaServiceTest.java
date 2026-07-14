package com.gymprofit.bot.services;

import com.gymprofit.bot.services.InsigniaService.Estado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica la evaluación pura de condiciones de insignia sobre el estado del jugador. */
class InsigniaServiceTest {

    private static Estado estado(int nivel, long coins, int poder, int mineria, int mundos,
                                 int estudios, boolean trabajo) {
        return new Estado(nivel, coins, poder, mineria, mundos, estudios, trabajo);
    }

    @Test
    void estadoNuloNoCumpleNadaYEstadoTopeCumpleTodo() {
        Estado bajo = estado(0, 0, 0, 0, 0, 0, false);
        Estado alto = estado(60, 200000, 200, 60, 8, 30, true);
        for (Insignias i : Insignias.CATALOGO) {
            assertFalse(InsigniaService.cumple(i, bajo), i.id() + " no debería cumplirse en vacío");
            assertTrue(InsigniaService.cumple(i, alto), i.id() + " debería cumplirse al tope");
        }
    }

    @Test
    void nivelYTrabajoSeEvaluanBien() {
        Insignias aprendiz = new Insignias("x", "", Insignias.Tipo.NIVEL, 5);
        assertTrue(InsigniaService.cumple(aprendiz, estado(5, 0, 0, 0, 0, 0, false)));
        assertFalse(InsigniaService.cumple(aprendiz, estado(4, 0, 0, 0, 0, 0, false)));

        Insignias trabajador = new Insignias("y", "", Insignias.Tipo.TRABAJO, 0);
        assertTrue(InsigniaService.cumple(trabajador, estado(0, 0, 0, 0, 0, 0, true)));
        assertFalse(InsigniaService.cumple(trabajador, estado(0, 0, 0, 0, 0, 0, false)));
    }

    @Test
    void coinsYPoderYMineriaYMundosYEstudios() {
        assertTrue(InsigniaService.cumple(new Insignias("a", "", Insignias.Tipo.COINS, 10000),
                estado(0, 10000, 0, 0, 0, 0, false)));
        assertTrue(InsigniaService.cumple(new Insignias("b", "", Insignias.Tipo.PODER, 50),
                estado(0, 0, 50, 0, 0, 0, false)));
        assertTrue(InsigniaService.cumple(new Insignias("c", "", Insignias.Tipo.MINERIA, 10),
                estado(0, 0, 0, 10, 0, 0, false)));
        assertTrue(InsigniaService.cumple(new Insignias("d", "", Insignias.Tipo.MUNDOS, 3),
                estado(0, 0, 0, 0, 3, 0, false)));
        assertTrue(InsigniaService.cumple(new Insignias("e", "", Insignias.Tipo.ESTUDIOS, 25),
                estado(0, 0, 0, 0, 0, 25, false)));
    }
}
