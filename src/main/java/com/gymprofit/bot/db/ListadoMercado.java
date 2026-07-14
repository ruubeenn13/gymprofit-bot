package com.gymprofit.bot.db;

/**
 * Un anuncio del mercado entre jugadores (fila de {@code mercado}).
 *
 * @param id       id del anuncio
 * @param vendedor jugador que vende
 * @param itemId   id del ítem (catálogo services/Items)
 * @param cantidad unidades disponibles (en escrow)
 * @param precio   precio por unidad en coins
 */
public record ListadoMercado(long id, long vendedor, String itemId, int cantidad, long precio) {
}
