package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de acciones de la bolsa ficticia (en código). El <b>precio</b> vive en BD (dinámico); aquí
 * van los datos fijos: emoji y <b>volatilidad</b> (cuánto puede moverse el precio en cada tick). El
 * nombre sale de i18n con clave {@code accion.<id>}. Todo es ficción.
 *
 * @param id         ticker (clave i18n y columna {@code acciones.id})
 * @param emoji      emoji para embeds
 * @param volatilidad fracción máxima de movimiento por tick (p. ej. 0.10 = ±10 %)
 */
public record Acciones(String id, String emoji, double volatilidad) {

    /** Catálogo completo. */
    public static final List<Acciones> CATALOGO = List.of(
            new Acciones("gymx", "🏋️", 0.05),
            new Acciones("protn", "🥛", 0.06),
            new Acciones("techv", "💻", 0.09),
            new Acciones("crpto", "🪙", 0.15),
            new Acciones("moonx", "🚀", 0.20),
            new Acciones("banko", "🏦", 0.03),
            new Acciones("gamez", "🎮", 0.10),
            new Acciones("memes", "🐸", 0.25));

    public static Optional<Acciones> porId(String id) {
        return CATALOGO.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    /**
     * Nuevo precio tras un tick: random walk acotado por la volatilidad. Función pura (azar en
     * {@code [0,1)}), con suelo en 1.
     *
     * @param precio      precio actual
     * @param volatilidad volatilidad de la acción
     * @param azar        muestra aleatoria en {@code [0,1)}
     */
    public static long mover(long precio, double volatilidad, double azar) {
        double factor = 1 + (azar * 2 - 1) * volatilidad; // en [1-vol, 1+vol)
        return Math.max(1, Math.round(precio * factor));
    }
}
