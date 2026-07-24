package com.gymprofit.bot.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifica la funcion pura {@link Prestigio#calcular(int, int, long)}: el nivel domina sobre el bote,
 * las tres componentes (nivel, miembros, bote) suman con sus pesos, una empresa vacia sin bote da 0 y
 * a igual nivel el numero de miembros desempata.
 */
class PrestigioTest {

    @Test
    void nivelDominaSobreBote() {
        assertTrue(Prestigio.calcular(2, 0, 0) > Prestigio.calcular(1, 0, 5_000_000),
                "un nivel mas vale mas que 5.000.000 de bote");
    }

    @Test
    void sumaLasTresComponentes() { // 3*10_000 + 4*1_000 + 2_500_000/1_000 = 36_500
        assertEquals(36_500L, Prestigio.calcular(3, 4, 2_500_000));
    }

    @Test
    void empresaVaciaSinBoteEsCero() {
        assertEquals(0L, Prestigio.calcular(0, 0, 0));
    }

    @Test
    void miembrosDesempatanAIgualNivel() {
        assertTrue(Prestigio.calcular(5, 3, 0) > Prestigio.calcular(5, 2, 0),
                "a igual nivel, mas miembros dan mas prestigio");
    }
}
