package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.moderacion.AplicadorSanciones;
import com.gymprofit.bot.commands.moderacion.ModHelper;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.DeteccionAbuso;
import com.gymprofit.bot.util.Cooldown;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Automoderación reactiva del chat (F1, SPEC): borra mensajes en flood (ráfaga) o con invitaciones a
 * otros servidores de Discord, y amonesta al autor reutilizando {@link AplicadorSanciones}. Requiere el
 * intent {@code MESSAGE_CONTENT} (ya habilitado en {@code Main}) para poder leer el texto del mensaje.
 * El personal ({@link ModHelper#esAltoCargo}) y los bots quedan exentos. El borrado es best-effort (no
 * rompe el flujo si Discord lo rechaza, p. ej. por falta de permisos). El anti-ráfaga (30&nbsp;s por
 * usuario) se recibe por constructor y se <b>comparte</b> con {@link AutoModWarnListener} (misma
 * instancia, cableada en {@code Main}): un mismo mensaje puede disparar ambos listeners (p. ej.
 * flood propio + regla nativa de spam de {@code /setup}), y sin compartir la ventana cada uno
 * amonestaría por su cuenta, duplicando el escalado. Mientras dura la ráfaga solo se borra el resto
 * de mensajes, sin volver a amonestar.
 */
public final class AntiAbusoListener extends ListenerAdapter {

    /** Nº máximo de usuarios distintos con historial en memoria antes de purgar (evita fuga). */
    private static final int CAP_USUARIOS = 5_000;

    private final AplicadorSanciones aplicador;

    // Historial de timestamps (epoch ms) por usuario, para detectar flood. Cada entrada se poda de
    // timestamps fuera de la ventana en cada mensaje, así que un usuario inactivo acaba con una
    // deque vacía; se limpia además el mapa entero si crece demasiado (tope duro, no LRU: es una
    // señal de abuso "blanda", no requiere precisión histórica).
    private final ConcurrentHashMap<Long, Deque<Long>> historial = new ConcurrentHashMap<>();

    /** Anti-ráfaga compartido con {@link AutoModWarnListener}: misma instancia, ver {@code Main}. */
    private final Cooldown antiRafaga;

    public AntiAbusoListener(AplicadorSanciones aplicador, Cooldown antiRafaga) {
        this.aplicador = aplicador;
        this.antiRafaga = antiRafaga;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent evento) {
        if (evento.getAuthor().isBot() || !evento.isFromGuild() || ModHelper.esAltoCargo(evento.getMember())) {
            return;
        }

        long ahora = System.currentTimeMillis();
        long usuarioId = evento.getAuthor().getIdLong();
        boolean flood = registrarYComprobarFlood(usuarioId, ahora);
        boolean invite = DeteccionAbuso.tieneInviteDiscord(evento.getMessage().getContentRaw());
        if (!flood && !invite) {
            return;
        }

        evento.getMessage().delete().queue(null, e -> { });

        if (!antiRafaga.intentar(usuarioId, ahora)) {
            return; // sigue dentro de la ventana de la última amonestación: solo se borra.
        }
        String motivo = Messages.get(Messages.ES,
                invite ? "moderacion.motivo.invite" : "moderacion.motivo.flood");
        aplicador.aplicar(evento.getGuild(), evento.getAuthor(), evento.getMember(),
                evento.getJDA().getSelfUser().getIdLong(), motivo);
    }

    /**
     * Añade el timestamp actual al historial del usuario, poda los que ya salieron de la ventana de
     * flood y comprueba si con eso se supera el umbral. Purga el mapa entero si acumula demasiados
     * usuarios distintos (tope duro para no crecer sin límite en memoria).
     */
    private boolean registrarYComprobarFlood(long usuarioId, long ahora) {
        if (historial.size() > CAP_USUARIOS) {
            historial.clear();
        }
        Deque<Long> ts = historial.computeIfAbsent(usuarioId, k -> new ConcurrentLinkedDeque<>());
        ts.addLast(ahora);
        long desde = ahora - DeteccionAbuso.FLOOD_SEG * 1000;
        Long cabeza;
        while ((cabeza = ts.peekFirst()) != null && cabeza < desde) {
            ts.pollFirst();
        }
        return DeteccionAbuso.esFlood(List.copyOf(ts), ahora);
    }
}
