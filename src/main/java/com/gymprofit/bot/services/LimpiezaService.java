package com.gymprofit.bot.services;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Limpieza de mensajes de un canal. Purga <b>solo</b> los mensajes de menos de 14 días, en bloque
 * (una petición por cada 100). Los más antiguos se ignoran a propósito: Discord no permite el
 * borrado en bloque de mensajes de más de 14 días y borrarlos uno a uno provoca una tormenta de
 * rate limits (429) que llega a desconectar el gateway.
 */
public final class LimpiezaService {

    /** Límites del número de mensajes a purgar en una operación. */
    public static final int MIN = 1;
    public static final int MAX = 1000;

    /** Margen bajo los 14 días para que ningún mensaje al borde falle el borrado en bloque. */
    private static final int DIAS_LIMITE = 14;

    /** Acota la cantidad pedida al rango permitido [MIN, MAX]. */
    public static int normalizar(int cantidad) {
        return Math.max(MIN, Math.min(MAX, cantidad));
    }

    /**
     * Purga hasta {@code cantidad} mensajes recientes (< 14 días) del canal, en bloque.
     *
     * @return futuro con el número de mensajes borrados (los de más de 14 días no se cuentan)
     */
    public CompletableFuture<Integer> purgarReciente(MessageChannel canal, int cantidad) {
        int n = normalizar(cantidad);
        OffsetDateTime limite = OffsetDateTime.now().minusDays(DIAS_LIMITE).plusMinutes(1);
        return canal.getIterableHistory().takeAsync(n).thenCompose(mensajes -> {
            List<Message> recientes = mensajes.stream()
                    .filter(m -> m.getTimeCreated().isAfter(limite))
                    .toList();
            if (recientes.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            // Todos < 14 días -> purgeMessages los borra en bloque (sin DELETE individuales).
            List<CompletableFuture<Void>> tareas = canal.purgeMessages(recientes);
            return CompletableFuture
                    .allOf(tareas.toArray(new CompletableFuture[0]))
                    .thenApply(v -> recientes.size());
        });
    }
}
