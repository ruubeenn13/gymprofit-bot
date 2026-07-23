package com.gymprofit.bot.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades de composición de embeds de Discord. Hoy solo el troceado de una lista larga de
 * líneas en bloques que respetan el tope de 4096 caracteres de la descripción de un embed.
 */
public final class Embeds {

    /** Tope de caracteres de la descripción de un embed de Discord. */
    public static final int MAX_DESC = 4096;

    private Embeds() {}

    /**
     * Reparte líneas en bloques cuyo texto unido con saltos de línea no supera {@code limite}
     * caracteres. Rompe solo entre líneas: nunca parte una línea por la mitad.
     */
    public static List<String> partirEnBloques(List<String> lineas, int limite) {
        List<String> bloques = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String linea : lineas) {
            if (actual.length() > 0 && actual.length() + 1 + linea.length() > limite) {
                bloques.add(actual.toString());
                actual.setLength(0);
            }
            if (actual.length() > 0) {
                actual.append('\n');
            }
            actual.append(linea);
        }
        if (actual.length() > 0) {
            bloques.add(actual.toString());
        }
        return bloques;
    }
}
