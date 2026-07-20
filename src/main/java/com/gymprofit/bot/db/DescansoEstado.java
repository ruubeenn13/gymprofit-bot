package com.gymprofit.bot.db;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Estado de descanso de un jugador (tabla {@code descanso}, 1:1 con {@code usuarios_discord}).
 *
 * @param discordId       jugador (snowflake)
 * @param dormidoDesde    instante en que se acostó, o {@code null} si está despierto
 * @param ultimoDespertar último despertar, o {@code null} si nunca durmió (para la fatiga)
 * @param consumidosHoy   consumibles usados en {@code diaConsumos} (saciedad)
 * @param diaConsumos     día natural al que pertenece {@code consumidosHoy}, o {@code null}
 * @param camaPagada      cama pagada al acostarse ({@code "hotel"}), o {@code null} si duerme en la
 *                        suya. El hotel no se posee, así que no puede salir del inventario al
 *                        despertar: hay que recordarlo
 */
public record DescansoEstado(long discordId, Instant dormidoDesde, Instant ultimoDespertar,
                             int consumidosHoy, LocalDate diaConsumos, String camaPagada) {

    /** {@code true} si el jugador está dormido ahora mismo. */
    public boolean dormido() {
        return dormidoDesde != null;
    }
}
