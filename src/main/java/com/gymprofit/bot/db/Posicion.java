package com.gymprofit.bot.db;

/**
 * Posición de un jugador en una acción (fila de {@code cartera}).
 *
 * @param accionId ticker
 * @param cantidad nº de acciones
 * @param coste    coste total invertido (para el P/L)
 */
public record Posicion(String accionId, long cantidad, long coste) {
}
