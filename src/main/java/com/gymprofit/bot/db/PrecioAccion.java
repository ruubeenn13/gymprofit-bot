package com.gymprofit.bot.db;

/**
 * Precio de una acción (fila de {@code acciones}).
 *
 * @param id      ticker
 * @param precio  precio actual
 * @param previo  precio anterior (para la tendencia ↑↓)
 */
public record PrecioAccion(String id, long precio, long previo) {
}
