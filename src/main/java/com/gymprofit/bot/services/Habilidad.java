package com.gymprofit.bot.services;

import java.util.Optional;

/**
 * Habilidades de combate (COMBAT-4b). Todo el mundo dispone del mismo set fijo; cada una tiene un
 * <b>cooldown en turnos</b> que se lleva en la {@link CombateSesion} (no hay schema). El efecto lo
 * aplica {@link BatallaService#usarHabilidad}. El nombre sale de i18n con clave {@code habilidad.<id>}.
 *
 * <ul>
 *   <li>{@code POTENTE}: golpe que dobla tu ataque (no puede ser esquivado).</li>
 *   <li>{@code CURAR}: recupera un porcentaje de tu HP de combate.</li>
 *   <li>{@code ATURDIR}: golpeas y el monstruo pierde su contraataque este turno.</li>
 * </ul>
 */
public enum Habilidad {
    POTENTE("golpe_potente", "💥", 3),
    CURAR("curar", "💚", 4),
    ATURDIR("aturdir", "💫", 4);

    private final String id;
    private final String emoji;
    private final int cooldown;

    Habilidad(String id, String emoji, int cooldown) {
        this.id = id;
        this.emoji = emoji;
        this.cooldown = cooldown;
    }

    public String id() {
        return id;
    }

    public String emoji() {
        return emoji;
    }

    /** Turnos de espera tras usarla. */
    public int cooldown() {
        return cooldown;
    }

    public static Optional<Habilidad> porId(String id) {
        for (Habilidad h : values()) {
            if (h.id.equals(id)) {
                return Optional.of(h);
            }
        }
        return Optional.empty();
    }
}
