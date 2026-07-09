package com.gymprofit.bot.util;

/**
 * Genera barras de progreso en texto para embeds (XP, retos, economía…). Usa bloques Unicode
 * para que se vean bien en Discord sin imágenes.
 */
public final class Barras {

    private static final char LLENO = '▰';
    private static final char VACIO = '▱';

    private Barras() {
    }

    /**
     * Barra de progreso con porcentaje, p. ej. {@code "▰▰▰▰▰▱▱▱▱▱ 50%"}.
     *
     * @param actual    valor actual ({@code < 0} se trata como 0)
     * @param total     valor que representa el 100 % ({@code <= 0} devuelve barra vacía)
     * @param segmentos número de bloques de la barra ({@code > 0})
     * @return la barra seguida del porcentaje
     */
    public static String progreso(long actual, long total, int segmentos) {
        long a = Math.max(0, actual);
        double ratio = (total <= 0) ? 0.0 : Math.min(1.0, (double) a / total);
        int llenos = (int) Math.round(ratio * segmentos);

        StringBuilder sb = new StringBuilder(segmentos + 5);
        sb.append(String.valueOf(LLENO).repeat(llenos));
        sb.append(String.valueOf(VACIO).repeat(segmentos - llenos));
        sb.append(' ').append(Math.round(ratio * 100)).append('%');
        return sb.toString();
    }
}
