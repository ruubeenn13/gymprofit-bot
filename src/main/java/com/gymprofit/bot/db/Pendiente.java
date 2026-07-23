package com.gymprofit.bot.db;

import com.gymprofit.bot.services.TipoPendiente;

import java.time.Instant;

/**
 * Una pertenencia pendiente de resolver (fila de {@code empresa_pendientes}): invitación de la
 * empresa al jugador, o solicitud del jugador a la empresa.
 *
 * @param id        id del pendiente
 * @param empresaId empresa implicada
 * @param discordId jugador implicado (snowflake)
 * @param tipo      dirección del pendiente (invitación o solicitud)
 * @param motivo    mensaje de la solicitud, o {@code null} en invitaciones
 * @param creada    instante de creación
 */
public record Pendiente(long id, long empresaId, long discordId, TipoPendiente tipo, String motivo,
                        Instant creada) {
}
