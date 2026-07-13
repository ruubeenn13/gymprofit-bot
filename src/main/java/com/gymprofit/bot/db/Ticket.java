package com.gymprofit.bot.db;

/**
 * Un ticket de soporte (tabla {@code tickets}): canal privado entre un usuario y el staff.
 *
 * @param id        id del ticket
 * @param discordId autor (snowflake)
 * @param canalId   canal privado creado, o {@code null}
 * @param estado    {@code ABIERTO} o {@code CERRADO}
 */
public record Ticket(long id, long discordId, long canalId, String estado) {
}
