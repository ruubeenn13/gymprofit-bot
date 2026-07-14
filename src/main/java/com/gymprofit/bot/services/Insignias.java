package com.gymprofit.bot.services;

import java.util.List;

/**
 * Catálogo de insignias/logros (F-ECO-3b, en código). Cada insignia tiene una condición sobre el
 * estado actual del jugador (nivel, coins, poder de combate, minería, mundos completados, estudios o
 * tener trabajo). {@link InsigniaService} las evalúa y persiste las nuevas. El nombre sale de i18n con
 * clave {@code insignia.<id>}.
 *
 * @param id     identificador estable (clave i18n y columna {@code insignias_ganadas.insignia_id})
 * @param emoji  emoji para embeds
 * @param tipo   qué se mide
 * @param umbral valor mínimo para desbloquearla (ignorado en {@link Tipo#TRABAJO})
 */
public record Insignias(String id, String emoji, Tipo tipo, long umbral) {

    /** Qué mide la condición de una insignia. */
    public enum Tipo { NIVEL, COINS, PODER, MINERIA, MUNDOS, ESTUDIOS, TRABAJO }

    /** Catálogo completo. */
    public static final List<Insignias> CATALOGO = List.of(
            new Insignias("aprendiz", "🎓", Tipo.NIVEL, 5),
            new Insignias("veterano", "⭐", Tipo.NIVEL, 25),
            new Insignias("leyenda_viva", "🌟", Tipo.NIVEL, 50),
            new Insignias("trabajador", "💼", Tipo.TRABAJO, 0),
            new Insignias("erudito", "📚", Tipo.ESTUDIOS, 25),
            new Insignias("adinerado", "💰", Tipo.COINS, 10000),
            new Insignias("ricachon", "🤑", Tipo.COINS, 100000),
            new Insignias("guerrero", "⚔️", Tipo.PODER, 50),
            new Insignias("campeon", "🏆", Tipo.PODER, 150),
            new Insignias("minero", "⛏️", Tipo.MINERIA, 10),
            new Insignias("maestro_minero", "💎", Tipo.MINERIA, 50),
            new Insignias("explorador", "🗺️", Tipo.MUNDOS, 3),
            new Insignias("conquistador", "👑", Tipo.MUNDOS, 8));
}
