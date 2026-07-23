package com.gymprofit.bot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbedsTest {

    @Test
    @DisplayName("partirEnBloques: ningún bloque supera el límite")
    void respetaLimite() {
        List<String> lineas = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            lineas.add("🔒 `puesto" + i + "` · **Puesto de ejemplo " + i + "** (sector) · 10-20 🪙 · req. nivel 5 — se llega por ascenso");
        }
        List<String> bloques = Embeds.partirEnBloques(lineas, Embeds.MAX_DESC);
        for (String b : bloques) {
            assertTrue(b.length() <= Embeds.MAX_DESC, "bloque de " + b.length() + " chars");
        }
        assertTrue(bloques.size() > 1, "200 líneas largas no caben en un solo embed");
    }

    @Test
    @DisplayName("partirEnBloques: no pierde ni parte líneas, mantiene el orden")
    void noParteLineas() {
        List<String> lineas = List.of("aaa", "bbb", "ccc", "ddd");
        List<String> bloques = Embeds.partirEnBloques(lineas, 7);
        List<String> reunidas = new ArrayList<>();
        for (String b : bloques) {
            reunidas.addAll(List.of(b.split("\n", -1)));
        }
        assertEquals(lineas, reunidas);
    }

    @Test
    @DisplayName("partirEnBloques: un solo bloque si todo cabe")
    void unBloqueSiCabe() {
        assertEquals(1, Embeds.partirEnBloques(List.of("hola", "mundo"), Embeds.MAX_DESC).size());
    }
}
