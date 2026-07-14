package com.gymprofit.bot.db;

import java.time.LocalDate;

/**
 * Cuenta de banco de un jugador (tabla {@code banco}).
 *
 * @param discordId     jugador
 * @param saldo         ahorro en el banco
 * @param prestamo      deuda pendiente del préstamo
 * @param ultimoInteres último día en que se aplicó interés, o {@code null} si nunca
 */
public record BancoCuenta(long discordId, long saldo, long prestamo, LocalDate ultimoInteres) {
}
