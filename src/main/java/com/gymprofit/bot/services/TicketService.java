package com.gymprofit.bot.services;

import com.gymprofit.bot.db.Ticket;
import com.gymprofit.bot.db.TicketRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.List;
import java.util.Optional;

/**
 * Lógica de los tickets de soporte: alta (garantizando la fila de usuario para la FK), consulta y
 * cierre con transcripción. La creación/borrado del canal privado la hace el listener; aquí va la
 * persistencia y el formateo de la transcripción (puro, testeable).
 */
public final class TicketService {

    private final TicketRepositorio tickets;
    private final UsuarioDiscordRepositorio usuarios;

    public TicketService(TicketRepositorio tickets, UsuarioDiscordRepositorio usuarios) {
        this.tickets = tickets;
        this.usuarios = usuarios;
    }

    public boolean tieneAbierto(long discordId) {
        return tickets.tieneAbierto(discordId);
    }

    /** Registra un ticket abierto (crea la fila de usuario si no existe, por la FK). */
    public long registrar(long discordId, long canalId, String asunto) {
        usuarios.obtenerOCrear(discordId);
        return tickets.abrir(discordId, canalId, asunto);
    }

    public Optional<Ticket> porCanal(long canalId) {
        return tickets.porCanal(canalId);
    }

    public void cerrar(long id, String transcript) {
        tickets.cerrar(id, transcript);
    }

    /**
     * Formatea la transcripción a partir de líneas {@code {autor, contenido}} (más antiguas primero).
     * Ignora mensajes sin contenido de texto (p. ej. solo-embed).
     */
    public static String formatearTranscript(List<String[]> lineas) {
        StringBuilder sb = new StringBuilder();
        for (String[] linea : lineas) {
            String contenido = linea[1] == null ? "" : linea[1].strip();
            if (contenido.isEmpty()) {
                continue;
            }
            sb.append(linea[0]).append(": ").append(contenido).append('\n');
        }
        return sb.toString().strip();
    }
}
