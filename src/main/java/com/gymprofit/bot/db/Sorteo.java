package com.gymprofit.bot.db;

/**
 * Un sorteo de la comunidad (tabla {@code sorteos}). El premio es texto público. El job de sorteos
 * usa {@code mensajeId} para leer las reacciones 🎉 y elegir ganadores al llegar {@code finEpoch}.
 *
 * @param id           id del sorteo
 * @param guildId      servidor (snowflake)
 * @param canalId      canal donde se publicó
 * @param mensajeId    mensaje del sorteo (para leer sus reacciones)
 * @param premio       premio (texto público)
 * @param numGanadores cuántos ganadores elegir
 * @param creadorId    staff que lo creó (snowflake)
 * @param finEpoch     instante de cierre (epoch en segundos)
 * @param activo       {@code false} cuando ya se resolvió
 */
public record Sorteo(long id, long guildId, long canalId, long mensajeId, String premio,
                     int numGanadores, long creadorId, long finEpoch, boolean activo) {
}
