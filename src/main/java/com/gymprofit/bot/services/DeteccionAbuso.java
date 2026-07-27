package com.gymprofit.bot.services;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detección pura de abuso en el chat (F1 moderación): flood (ráfaga de mensajes en poco tiempo) e
 * invitaciones a otros servidores de Discord. Sin estado ni JDA: el listener le pasa los timestamps y el
 * texto. El estado (historial por usuario) y las acciones (borrar, warn) viven en el listener.
 */
public final class DeteccionAbuso {

    private DeteccionAbuso() {}

    /** Mensajes que disparan el flood. */
    public static final int FLOOD_MSGS = 5;
    /** Ventana del flood en segundos. */
    public static final long FLOOD_SEG = 7;

    private static final Pattern INVITE =
            Pattern.compile("(?i)(discord\\.gg/|discord(app)?\\.com/invite/)\\S+");

    /** ¿Hay ≥ {@link #FLOOD_MSGS} mensajes en los últimos {@link #FLOOD_SEG} s hasta {@code ahora} (epoch ms)? */
    public static boolean esFlood(List<Long> timestamps, long ahora) {
        long desde = ahora - FLOOD_SEG * 1000;
        long enVentana = timestamps.stream().filter(t -> t >= desde && t <= ahora).count();
        return enVentana >= FLOOD_MSGS;
    }

    /** ¿El texto contiene una invitación a un servidor de Discord? */
    public static boolean tieneInviteDiscord(String texto) {
        return texto != null && INVITE.matcher(texto).find();
    }
}
