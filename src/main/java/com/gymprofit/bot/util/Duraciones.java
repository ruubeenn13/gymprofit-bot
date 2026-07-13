package com.gymprofit.bot.util;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsea duraciones cortas del estilo {@code 30m}, {@code 1h}, {@code 2d}, {@code 1d12h30m} a
 * segundos. Unidades: {@code d} (días), {@code h} (horas), {@code m} (minutos), {@code s} (segundos).
 * Sirve para {@code /timeout} y {@code /mute} temporal.
 */
public final class Duraciones {

    /** Timeout nativo de Discord: máximo 28 días. */
    public static final long MAX_TIMEOUT_SEG = 28L * 24 * 3600;

    private static final Pattern TOKEN = Pattern.compile("(\\d+)([dhms])");

    private Duraciones() {
    }

    /**
     * Convierte una expresión como {@code 1d2h30m} a segundos. Devuelve vacío si el texto es nulo,
     * está vacío, no casa el formato o suma cero.
     */
    public static OptionalLong parsear(String texto) {
        if (texto == null || texto.isBlank()) {
            return OptionalLong.empty();
        }
        String limpio = texto.trim().toLowerCase();
        Matcher m = TOKEN.matcher(limpio);
        long total = 0;
        int consumido = 0;
        while (m.find()) {
            if (m.start() != consumido) {
                return OptionalLong.empty(); // hay caracteres sueltos entre tokens
            }
            long valor = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> valor * 24 * 3600;
                case "h" -> valor * 3600;
                case "m" -> valor * 60;
                default -> valor; // "s"
            };
            consumido = m.end();
        }
        if (consumido != limpio.length() || total <= 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(total);
    }

    /** Formatea segundos como {@code 1d 2h 30m 15s} (omite las unidades a cero). */
    public static String formatear(long segundos) {
        if (segundos <= 0) {
            return "0s";
        }
        long d = segundos / 86_400;
        long h = (segundos % 86_400) / 3600;
        long min = (segundos % 3600) / 60;
        long s = segundos % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append("d ");
        }
        if (h > 0) {
            sb.append(h).append("h ");
        }
        if (min > 0) {
            sb.append(min).append("m ");
        }
        if (s > 0) {
            sb.append(s).append("s");
        }
        return sb.toString().trim();
    }
}
