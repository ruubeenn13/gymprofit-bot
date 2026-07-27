package com.gymprofit.bot.events;

import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.TriviaService;
import com.gymprofit.bot.services.TriviaService.Resultado;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Locale;

/**
 * Conduce la respuesta a una pregunta de trivia (F4). Escucha los botones de opción (customId
 * {@code trivia:resp:<preguntaId>:<letra>}); la validación one-shot y el pago del premio los hace
 * {@link TriviaService#responder}. Al resolver, edita el propio mensaje (efímero, solo lo ve quien juega)
 * con el desenlace y le quita los botones para que la pregunta no se pueda re-contestar.
 */
public final class TriviaListener extends ListenerAdapter {

    private static final String PREFIJO = "trivia:resp:";

    private final TriviaService trivia;

    public TriviaListener(TriviaService trivia) {
        this.trivia = trivia;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        if (!id.startsWith(PREFIJO)) {
            return;
        }
        // customId: trivia:resp:<preguntaId>:<letra>
        String[] partes = id.split(":");
        long preguntaId = Long.parseUnsignedLong(partes[2]);
        char letra = partes[3].charAt(0);
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        boolean es = locale == Messages.ES;
        long actorId = evento.getUser().getIdLong();
        Resultado r = trivia.responder(actorId, preguntaId, letra, es);
        String texto = switch (r.estado()) {
            case ACIERTO -> Messages.get(locale, "trivia.acierto",
                    r.coins(), r.xp(), trivia.aciertosDe(actorId), trivia.total());
            case FALLO -> Messages.get(locale, "trivia.fallo",
                    String.valueOf(r.correcta()), trivia.aciertosDe(actorId), trivia.total());
            case YA_RESPONDIDA -> Messages.get(locale, "trivia.ya_respondida");
            case NO_EXISTE -> Messages.get(locale, "trivia.error");
        };
        // Un acierto luce como logro (dorado); el resto mantiene el azul de trivia. Se quitan los botones
        // para cerrar la pregunta (además, el gate one-shot del service ya impediría cobrar de nuevo).
        EmbedFactory.Tipo tipo = r.estado() == TriviaService.Estado.ACIERTO
                ? EmbedFactory.Tipo.LOGRO : EmbedFactory.Tipo.TRIVIA;
        evento.editMessageEmbeds(EmbedFactory.aviso(tipo, locale, texto)).setComponents().queue();
    }
}
