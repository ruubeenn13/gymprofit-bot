package com.gymprofit.bot.services;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Limpieza de mensajes de un canal. Purga los mensajes recientes (Discord solo permite el
 * borrado en bloque de mensajes de menos de 14 días; los más antiguos no se tocan).
 */
public final class LimpiezaService {

    /** Límites del número de mensajes a purgar en una operación. */
    public static final int MIN = 1;
    public static final int MAX = 1000;

    /** Acota la cantidad pedida al rango permitido [MIN, MAX]. */
    public static int normalizar(int cantidad) {
        return Math.max(MIN, Math.min(MAX, cantidad));
    }

    /**
     * Purga hasta {@code cantidad} mensajes recientes del canal.
     *
     * @return futuro con el número de mensajes que se intentaron borrar
     */
    public CompletableFuture<Integer> purgarReciente(MessageChannel canal, int cantidad) {
        int n = normalizar(cantidad);
        return canal.getIterableHistory().takeAsync(n).thenCompose(mensajes -> {
            List<CompletableFuture<Void>> tareas = canal.purgeMessages(mensajes);
            return CompletableFuture
                    .allOf(tareas.toArray(new CompletableFuture[0]))
                    .thenApply(v -> mensajes.size());
        });
    }
}
