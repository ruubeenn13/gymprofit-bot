package com.gymprofit.bot.db;

/**
 * Estado de RPG de un personaje (tabla {@code personajes}, 1:1 con {@code usuarios_discord}). El
 * monedero (coins) vive en {@code usuarios_discord}; aquí van los atributos, la energía y la salud.
 *
 * @param discordId   usuario (snowflake)
 * @param fuerza      atributo Fuerza
 * @param resistencia atributo Resistencia
 * @param carisma     atributo Carisma
 * @param energia     energía 0-100
 * @param salud       salud 0-100
 */
public record Personaje(long discordId, int fuerza, int resistencia, int carisma,
                        int energia, int salud) {
}
