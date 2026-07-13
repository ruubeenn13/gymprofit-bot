package com.gymprofit.bot.db;

/**
 * Una sugerencia de la comunidad (tabla {@code sugerencias}): un post en el foro con votación 👍/👎
 * y un estado que resuelve el staff.
 *
 * @param id         id de la sugerencia
 * @param discordId  autor (snowflake)
 * @param mensajeId  id del post del foro (thread) para poder editarlo/etiquetarlo
 * @param contenido  texto de la sugerencia
 * @param estado     PENDIENTE, ACEPTADA o RECHAZADA
 */
public record Sugerencia(long id, long discordId, long mensajeId, String contenido, String estado) {
}
