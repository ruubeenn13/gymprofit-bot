package com.gymprofit.bot.db;

/**
 * Un gremio (fila de {@code gremios}).
 *
 * @param id      id del gremio
 * @param nombre  nombre único
 * @param dueno   jugador dueño/fundador
 * @param canalId id del canal privado en Discord, o {@code null} si aún no se creó
 */
public record Gremio(long id, String nombre, long dueno, Long canalId) {
}
