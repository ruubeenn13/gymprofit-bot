package com.gymprofit.bot.events;

import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.DueloService;
import com.gymprofit.bot.services.DueloService.Duelo;
import com.gymprofit.bot.services.DueloService.Resultado;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Locale;
import java.util.Optional;

/**
 * Conduce la confirmación de los duelos (F-ECO-6). Escucha los botones Aceptar/Rechazar (customId
 * {@code duelo:<acc|rej>:<retadoId>:<dueloId>}); solo el retado puede pulsarlos. Al aceptar, resuelve
 * el duelo (cobra a ambos y paga al ganador) y edita el mensaje con el desenlace.
 */
public final class DueloListener extends ListenerAdapter {

    private static final String PREFIJO = "duelo:";

    private final DueloService duelos;

    public DueloListener(DueloService duelos) {
        this.duelos = duelos;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        if (!id.startsWith(PREFIJO) || evento.getGuild() == null) {
            return;
        }
        String[] partes = id.split(":");
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long retadoId = Long.parseUnsignedLong(partes[2]);
        long dueloId = Long.parseUnsignedLong(partes[3]);

        if (evento.getUser().getIdLong() != retadoId) {
            evento.reply(Messages.get(locale, "duelo.noestuyo")).setEphemeral(true).queue();
            return;
        }
        Optional<Duelo> duelo = duelos.consumir(dueloId);
        if (duelo.isEmpty()) {
            evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.STATS, locale,
                    Messages.get(locale, "duelo.caducado"))).setComponents().queue();
            return;
        }
        if (partes[1].equals("rej")) {
            evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.STATS, locale,
                    Messages.get(locale, "duelo.rechazado"))).setComponents().queue();
            return;
        }
        Resultado r = duelos.resolver(duelo.get());
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "duelo.ganador", "<@" + r.ganador() + ">",
                    duelo.get().apuesta() * 2);
            case RETADOR_SIN_SALDO -> Messages.get(locale, "duelo.retadorsinsaldo");
            case RETADO_SIN_SALDO -> Messages.get(locale, "duelo.retadosinsaldo");
        };
        evento.editMessageEmbeds(EmbedFactory.aviso(
                r.estado() == DueloService.Estado.OK ? EmbedFactory.Tipo.LOGRO : EmbedFactory.Tipo.STATS,
                locale, mensaje)).setComponents().queue();
    }
}
