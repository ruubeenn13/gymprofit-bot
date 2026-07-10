package com.gymprofit.bot.db;

/**
 * Reto de la semana y próximo evento de un servidor (tabla {@code eventos_servidor}). Alimenta los
 * contadores «🎯 Reto» y «⏳ Evento» de la categoría SERVER STATS. {@code null} = aún sin fijar.
 *
 * @param guildId      ID del servidor de Discord
 * @param retoTexto    texto del reto de la semana, o {@code null}
 * @param eventoNombre nombre del próximo evento, o {@code null}
 * @param eventoFin    instante del evento en epoch (segundos) para la cuenta atrás, o {@code null}
 */
public record EventoServidor(long guildId, String retoTexto, String eventoNombre, Long eventoFin) {

    /** Registro vacío de un servidor: sin reto ni evento fijados. */
    public static EventoServidor vacio(long guildId) {
        return new EventoServidor(guildId, null, null, null);
    }
}
