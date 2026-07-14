package com.gymprofit.bot.events;

import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.TruequeRegistro;
import com.gymprofit.bot.services.TruequeService;
import com.gymprofit.bot.services.TruequeService.Estado;
import com.gymprofit.bot.services.TruequeService.Oferta;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Locale;
import java.util.Optional;

/**
 * Conduce la confirmación de los trueques (F-ECO-4d). Escucha los botones Aceptar/Rechazar (customId
 * {@code trueque:<acc|rej>:<objetivoId>:<ofertaId>}); solo el objetivo puede pulsarlos. Al aceptar,
 * ejecuta el intercambio (atómico con rollback) y edita el mensaje con el resultado.
 */
public final class TruequeListener extends ListenerAdapter {

    private static final String PREFIJO = "trueque:";

    private final TruequeService trueque;
    private final TruequeRegistro registro;

    public TruequeListener(TruequeService trueque, TruequeRegistro registro) {
        this.trueque = trueque;
        this.registro = registro;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        if (!id.startsWith(PREFIJO) || evento.getGuild() == null) {
            return;
        }
        String[] partes = id.split(":");
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long objetivoId = Long.parseUnsignedLong(partes[2]);
        long ofertaId = Long.parseUnsignedLong(partes[3]);

        // Solo el destinatario del trueque decide.
        if (evento.getUser().getIdLong() != objetivoId) {
            evento.reply(Messages.get(locale, "trueque.noestuyo")).setEphemeral(true).queue();
            return;
        }
        Optional<Oferta> oferta = registro.consumir(ofertaId);
        if (oferta.isEmpty()) {
            evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "trueque.caducado"))).setComponents().queue();
            return;
        }
        if (partes[1].equals("rej")) {
            evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "trueque.rechazado"))).setComponents().queue();
            return;
        }
        Estado r = trueque.ejecutar(oferta.get());
        String mensaje = switch (r) {
            case OK -> Messages.get(locale, "trueque.hecho");
            case PROPONENTE_SIN_SALDO, PROPONENTE_SIN_ITEM ->
                    Messages.get(locale, "trueque.fallo.proponente");
            case OBJETIVO_SIN_SALDO, OBJETIVO_SIN_ITEM ->
                    Messages.get(locale, "trueque.fallo.objetivo");
        };
        evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje))
                .setComponents().queue();
    }
}
