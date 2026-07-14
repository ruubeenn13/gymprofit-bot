package com.gymprofit.bot.services;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de encantamientos de arma (COMBAT-4c, en código). Cada encantamiento aplica <b>un</b>
 * efecto al combate del jugador, que se resuelve al iniciar la pelea (daño plano/porcentual y
 * crítico/esquiva se fijan en la sesión; el robo de vida cura por golpe). El nombre sale de i18n con
 * clave {@code encanto.<id>}. Precio anclado a la escala económica lenta.
 *
 * @param id       identificador estable (clave i18n y columna {@code personajes.arma_encanto})
 * @param emoji    emoji para embeds
 * @param precio   coste en coins de aplicarlo
 * @param tipo     tipo de efecto
 * @param magnitud magnitud del efecto (daño plano: puntos; % y prob.: fracción 0-1)
 */
public record Encantamiento(String id, String emoji, long precio, Tipo tipo, double magnitud) {

    /** Tipo de efecto de un encantamiento (cómo modifica el combate del jugador). */
    public enum Tipo {
        /** Suma daño plano a cada golpe. */
        DANO_PLANO,
        /** Multiplica el daño de cada golpe por (1 + magnitud). */
        DANO_PCT,
        /** Cura al jugador un % del daño infligido (robo de vida). */
        ROBO_VIDA,
        /** Aumenta la probabilidad de crítico. */
        CRITICO,
        /** Aumenta la probabilidad de esquiva. */
        ESQUIVA
    }

    /** Catálogo completo. */
    public static final List<Encantamiento> CATALOGO = List.of(
            new Encantamiento("afilado",  "⚔️", 1500,  Tipo.DANO_PLANO, 3),
            new Encantamiento("veneno",   "☠️", 3000,  Tipo.DANO_PLANO, 6),
            new Encantamiento("llama",    "🔥", 5000,  Tipo.DANO_PCT,   0.20),
            new Encantamiento("tormenta", "🌩️", 12000, Tipo.DANO_PCT,   0.35),
            new Encantamiento("vampirico","🩸", 8000,  Tipo.ROBO_VIDA,  0.25),
            new Encantamiento("sagrado",  "✨", 18000, Tipo.ROBO_VIDA,  0.40),
            new Encantamiento("preciso",  "⚡", 6000,  Tipo.CRITICO,    0.12),
            new Encantamiento("runico",   "🔮", 15000, Tipo.CRITICO,    0.20),
            new Encantamiento("escarcha", "❄️", 7000,  Tipo.ESQUIVA,    0.10));

    public static Optional<Encantamiento> porId(String id) {
        return CATALOGO.stream().filter(e -> e.id().equals(id)).findFirst();
    }
}
