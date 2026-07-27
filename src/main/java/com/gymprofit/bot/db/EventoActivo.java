package com.gymprofit.bot.db;

import com.gymprofit.bot.services.EventoEconomico;

import java.time.Instant;

/**
 * Evento del clima económico actualmente activo, tal y como está persistido (F5). Es la fila única de
 * {@code evento_economico}: qué evento y su ventana temporal. {@link #caducado(Instant)} decide si ya
 * expiró (lo comprueba el job en cada pasada).
 *
 * @param tipo   evento del catálogo
 * @param inicio cuándo empezó
 * @param fin    cuándo caduca (inicio + duración)
 */
public record EventoActivo(EventoEconomico tipo, Instant inicio, Instant fin) {

    /** ¿Ya expiró a fecha {@code ahora}? (fin inclusive). */
    public boolean caducado(Instant ahora) {
        return !ahora.isBefore(fin);
    }
}
