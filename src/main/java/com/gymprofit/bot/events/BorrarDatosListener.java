package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.privacidad.PrivacidadComando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.PrivacidadService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.Locale;

/**
 * Maneja el botón de confirmación de {@code /borrar-mis-datos}: borra los datos del <b>propio</b>
 * usuario que pulsa (el mensaje es efímero, solo lo ve él) y edita el mensaje con el resultado.
 */
public final class BorrarDatosListener extends ListenerAdapter {

    private final PrivacidadService privacidad;

    public BorrarDatosListener(PrivacidadService privacidad) {
        this.privacidad = privacidad;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        if (!PrivacidadComando.BOTON_CONFIRMAR.equals(evento.getComponentId())) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferEdit().queue();
        privacidad.borrar(evento.getUser().getIdLong());
        var embed = EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale,
                Messages.get(locale, "borrar.hecho.titulo"),
                Messages.get(locale, "borrar.hecho.texto")).build();
        // Quita el botón y muestra la confirmación.
        evento.getHook().editOriginalEmbeds(embed).setComponents(List.of()).queue();
    }
}
