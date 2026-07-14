package com.gymprofit.bot.services;

/**
 * Rango del jugador según su nivel (F-ECO-3). Cada rango se corresponde con un <b>rol</b> creado por
 * {@code /setup} (mismo nombre, con su emoji); {@link RangoService} lo asigna automáticamente al subir
 * de nivel. Los umbrales son crecientes: el rango de un nivel es el más alto cuyo {@link #nivelMin} no
 * lo supera.
 */
public enum Rango {
    NOVATO("🏅 Novato", 0),
    HABITUAL("🏅 Habitual", 10),
    VETERANO("🏅 Veterano", 25),
    LEYENDA("🏅 Leyenda", 50);

    private final String rolNombre;
    private final int nivelMin;

    Rango(String rolNombre, int nivelMin) {
        this.rolNombre = rolNombre;
        this.nivelMin = nivelMin;
    }

    /** Nombre exacto del rol de Discord (creado por /setup). */
    public String rolNombre() {
        return rolNombre;
    }

    /** Nivel mínimo para alcanzar este rango. */
    public int nivelMin() {
        return nivelMin;
    }

    /** Rango correspondiente a un nivel (el más alto alcanzado). */
    public static Rango para(int nivel) {
        Rango actual = NOVATO;
        for (Rango r : values()) {
            if (nivel >= r.nivelMin) {
                actual = r;
            }
        }
        return actual;
    }
}
