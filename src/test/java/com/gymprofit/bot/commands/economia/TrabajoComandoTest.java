package com.gymprofit.bot.commands.economia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests del troceo de la lista de trabajos en bloques de embed. Regresión: {@code /trabajo lista}
 * reventaba con {@code Description cannot be longer than 4096 characters} cuando casi todo el
 * catálogo salía con candado (líneas más largas) para un jugador sin carrera.
 */
class TrabajoComandoTest {

    @Test
    @DisplayName("partirEnBloques: ningún bloque supera el límite")
    void respetaLimite() {
        List<String> lineas = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            lineas.add("🔒 `puesto" + i + "` · **Puesto de ejemplo " + i + "** (sector) · 10-20 🪙 · req. nivel 5 — se llega por ascenso");
        }
        List<String> bloques = partir(lineas, 4096);
        for (String b : bloques) {
            assertTrue(b.length() <= 4096, "bloque de " + b.length() + " chars");
        }
        assertTrue(bloques.size() > 1, "200 líneas largas no caben en un solo embed");
    }

    @Test
    @DisplayName("partirEnBloques: no pierde ni parte líneas, mantiene el orden")
    void noParteLineas() {
        List<String> lineas = List.of("aaa", "bbb", "ccc", "ddd");
        List<String> bloques = partir(lineas, 7);  // "aaa\nbbb" = 7 justo; "ccc" ya no cabe
        List<String> reunidas = new ArrayList<>();
        for (String b : bloques) {
            reunidas.addAll(List.of(b.split("\n", -1)));
        }
        assertEquals(lineas, reunidas);
    }

    @Test
    @DisplayName("partirEnBloques: un solo bloque si todo cabe")
    void unBloqueSiCabe() {
        assertEquals(1, partir(List.of("hola", "mundo"), 4096).size());
    }

    private static List<String> partir(List<String> lineas, int limite) {
        return TrabajoComando.partirEnBloques(lineas, limite);
    }
}
