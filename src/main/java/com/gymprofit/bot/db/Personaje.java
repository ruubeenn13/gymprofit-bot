package com.gymprofit.bot.db;

import java.time.Instant;

/**
 * Estado de RPG de un personaje (tabla {@code personajes}, 1:1 con {@code usuarios_discord}). El
 * monedero (coins) vive en {@code usuarios_discord}; aquí van los atributos, la energía, la salud,
 * el trabajo actual y el cooldown de {@code /work}.
 *
 * @param discordId   usuario (snowflake)
 * @param fuerza      atributo Fuerza
 * @param resistencia atributo Resistencia
 * @param carisma     atributo Carisma
 * @param energia     energía 0-100
 * @param salud       salud 0-100
 * @param trabajo     id del trabajo actual (catálogo en código), o {@code null} si está en paro
 * @param ultimoWork  última vez que usó {@code /work}, o {@code null} si nunca
 */
public record Personaje(long discordId, int fuerza, int resistencia, int carisma,
                        int energia, int salud, String trabajo, Instant ultimoWork) {
}
