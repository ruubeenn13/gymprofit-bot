package com.gymprofit.bot.db;

import java.time.Instant;

/**
 * Una acción de moderación registrada en la tabla {@code sanciones} (auditoría / accountability).
 * {@code motivo} y {@code nickAnterior} viajan <b>cifrados</b> entre repo y BD; el servicio los
 * descifra al leerlos (ver {@code util/Cifrador}).
 *
 * @param id           id de la sanción
 * @param guildId      servidor (snowflake)
 * @param discordId    usuario sancionado (snowflake)
 * @param moderadorId  staff que la aplicó (snowflake)
 * @param tipo         WARN, MUTE, UNMUTE, TIMEOUT, UNTIMEOUT, KICK, BAN, UNBAN, NICK
 * @param motivo       razón (en claro tras el servicio; cifrada en BD), o {@code null}
 * @param nickAnterior apodo previo (solo NICK; cifrado en BD), o {@code null}
 * @param duracionSeg  duración en segundos (TIMEOUT/MUTE temporal), o {@code null}
 * @param creadoEn     instante de la sanción
 */
public record Sancion(long id, long guildId, long discordId, long moderadorId, String tipo,
                      String motivo, String nickAnterior, Long duracionSeg, Instant creadoEn) {
}
