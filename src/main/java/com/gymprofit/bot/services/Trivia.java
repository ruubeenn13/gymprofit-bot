package com.gymprofit.bot.services;

/**
 * Números puros de la trivia (F4): la recompensa por acertar según la dificultad. Sin estado. El premio
 * total que un jugador puede obtener está acotado (una vez por pregunta), así que es un faucet finito.
 */
public final class Trivia {
    private Trivia() {}
    /** Recompensa de un acierto: coins + XP. */
    public record Recompensa(long coins, int xp) {}
    /** Recompensa según la dificultad (FACIL/MEDIA/DIFICIL); desconocida -> MEDIA. */
    public static Recompensa recompensa(String dificultad) {
        return switch (dificultad == null ? "MEDIA" : dificultad) {
            case "FACIL" -> new Recompensa(40, 20);
            case "DIFICIL" -> new Recompensa(120, 50);
            default -> new Recompensa(80, 35); // MEDIA
        };
    }
}
