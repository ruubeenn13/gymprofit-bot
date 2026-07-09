package com.gymprofit.bot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica la barra de progreso: proporción de bloques llenos, porcentaje y bordes (0, lleno,
 * desbordado y total no válido).
 */
class BarrasTest {

    @Test
    void barraVacia() {
        String barra = Barras.progreso(0, 100, 10);
        assertTrue(barra.endsWith("0%"));
        assertEquals(10, contar(barra, '▱'));
        assertEquals(0, contar(barra, '▰'));
    }

    @Test
    void barraMedia() {
        String barra = Barras.progreso(50, 100, 10);
        assertEquals(5, contar(barra, '▰'));
        assertEquals(5, contar(barra, '▱'));
        assertTrue(barra.endsWith("50%"));
    }

    @Test
    void barraLlenaYDesbordada() {
        assertTrue(Barras.progreso(100, 100, 10).endsWith("100%"));
        String desbordada = Barras.progreso(150, 100, 10);
        assertEquals(10, contar(desbordada, '▰'), "Se limita al 100 %");
        assertTrue(desbordada.endsWith("100%"));
    }

    @Test
    void totalNoValidoDaBarraVacia() {
        String barra = Barras.progreso(10, 0, 10);
        assertEquals(10, contar(barra, '▱'));
        assertTrue(barra.endsWith("0%"));
    }

    private static int contar(String texto, char c) {
        return (int) texto.chars().filter(ch -> ch == c).count();
    }
}
