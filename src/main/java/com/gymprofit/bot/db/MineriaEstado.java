package com.gymprofit.bot.db;

import java.time.Instant;

/**
 * Estado de minería de un jugador (tabla {@code mineria}, 1:1 con {@code usuarios_discord}).
 *
 * @param discordId    jugador (snowflake)
 * @param nivelMineria nivel de minería (sube con el uso de {@code /minar})
 * @param ultimoMinado última vez que minó, o {@code null} si nunca (cooldown)
 */
public record MineriaEstado(long discordId, int nivelMineria, Instant ultimoMinado) {
}
