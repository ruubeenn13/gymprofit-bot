package com.gymprofit.bot.db;

import com.gymprofit.bot.services.RangoEmpresa;

import java.time.Instant;

/**
 * Pertenencia de un jugador a una empresa (fila de {@code empresa_miembros}). La pertenencia es
 * exclusiva: un jugador solo puede estar en una empresa a la vez.
 *
 * @param empresaId empresa a la que pertenece
 * @param discordId jugador miembro (snowflake)
 * @param rango     rango interno dentro de la empresa
 * @param seUnio    instante de alta en la empresa
 */
public record MiembroEmpresa(long empresaId, long discordId, RangoEmpresa rango,
                             Instant seUnio) {
}
