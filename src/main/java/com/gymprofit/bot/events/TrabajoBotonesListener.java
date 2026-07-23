package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.economia.TrabajoComando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.TrabajoService;
import com.gymprofit.bot.services.TrabajoService.ResultadoDimitir;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Locale;

/**
 * Botones de la confirmación de {@code /trabajo dimitir}: Sí (renuncia) / No (cancela). Se separa del
 * comando porque JDA enruta los botones por evento, no por el comando que los emitió.
 *
 * <p>El customId es {@code trabajo:dimitir:<accion>:<ownerId>}: el mensaje de confirmación es efímero,
 * pero se valida igualmente el dueño por coherencia con el resto de botones del bot
 * ({@code DescansoListener} / {@code CombateListener}).
 */
public final class TrabajoBotonesListener extends ListenerAdapter {

    private final TrabajoService trabajos;

    public TrabajoBotonesListener(TrabajoService trabajos) {
        this.trabajos = trabajos;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        boolean si = id.startsWith(TrabajoComando.BOTON_DIMITIR_SI);
        boolean no = id.startsWith(TrabajoComando.BOTON_DIMITIR_NO);
        if (!si && !no) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());

        // customId = "trabajo:dimitir:<accion>:<ownerId>": solo el dueño resuelve su dimisión.
        String[] partes = id.split(":");
        if (partes.length < 4 || evento.getUser().getIdLong() != Long.parseUnsignedLong(partes[3])) {
            evento.reply(Messages.get(locale, "dimitir.noestuyo")).setEphemeral(true).queue();
            return;
        }

        if (no) {
            // Cancela: se quitan los botones para que el embed no pueda repulsarse.
            evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "dimitir.cancelado"))).setComponents().queue();
            return;
        }

        long userId = evento.getUser().getIdLong();
        // Se lee el puesto ANTES de dimitir: al ejecutarse queda en null y ya no se podría nombrar.
        String puesto = trabajos.infoCarrera(userId).puestoActual();
        ResultadoDimitir r = trabajos.dimitir(userId);
        String desc = switch (r) {
            case OK -> Messages.get(locale, "dimitir.ok", Messages.get(locale, "trabajo." + puesto));
            // Dimitió entre la confirmación y el clic (dos pestañas, otro comando): sin trabajo ya.
            case SIN_TRABAJO -> Messages.get(locale, "dimitir.sintrabajo");
        };
        evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, desc))
                .setComponents().queue();
    }
}
