package com.gymprofit.bot.services;

/**
 * Curva de nivel del bot. Relaciona la XP total acumulada con el nivel alcanzado.
 *
 * <p>Fórmula (documentada aquí como fuente de verdad): la XP total necesaria para <b>alcanzar</b>
 * el nivel {@code n} es {@code 50·n² + 50·n}. Es decir:</p>
 * <ul>
 *   <li>nivel 1 → 100 XP, nivel 2 → 300, nivel 3 → 600, nivel 4 → 1000, nivel 5 → 1500…</li>
 * </ul>
 * <p>El salto entre niveles crece de forma lineal (100, 200, 300, 400…), de modo que subir
 * cuesta cada vez un poco más sin dispararse.</p>
 */
public final class NivelCalculadora {

    private NivelCalculadora() {
    }

    /**
     * XP total acumulada necesaria para alcanzar un nivel.
     *
     * @param nivel nivel objetivo ({@code >= 0}); nivel 0 = 0 XP
     * @return XP total requerida
     */
    public static int xpParaNivel(int nivel) {
        return 50 * nivel * nivel + 50 * nivel;
    }

    /**
     * Nivel correspondiente a una XP total.
     *
     * @param xp XP total acumulada
     * @return nivel alcanzado ({@code 0} si la XP es 0 o negativa)
     */
    public static int nivelDeXp(int xp) {
        if (xp <= 0) {
            return 0;
        }
        int nivel = 0;
        while (xpParaNivel(nivel + 1) <= xp) {
            nivel++;
        }
        return nivel;
    }
}
