package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.economia.DescansoComando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.DescansoService;
import com.gymprofit.bot.services.DescansoService.ResultadoDespertar;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.time.Instant;
import java.util.Locale;

/**
 * Botones del embed «😴 Estás dormido» que sale al intentar actuar durmiendo (currar, pelear, minar):
 * seguir durmiendo o despertar.
 *
 * <p>El bot <b>nunca despierta solo</b> a nadie: perder el descanso sin querer da rabia, así que
 * levantarse es siempre una decisión explícita. Por eso el bloqueo ofrece la salida aquí mismo en vez
 * de obligar a lanzar {@code /descansar despertar} aparte.
 *
 * <p>El customId es {@code descanso:<accion>:<ownerId>}: el mensaje es público, así que lleva el
 * dueño para que nadie despierte a otro (patrón de {@code CombateListener} / {@code TruequeListener}).
 */
public final class DescansoListener extends ListenerAdapter {

    private final DescansoService descanso;

    public DescansoListener(DescansoService descanso) {
        this.descanso = descanso;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        boolean seguir = id.startsWith(DescansoComando.BOTON_SEGUIR);
        if (!seguir && !id.startsWith(DescansoComando.BOTON_DESPERTAR)) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());

        // customId = "descanso:<accion>:<ownerId>": solo el dueño maneja su descanso.
        String[] partes = id.split(":");
        if (partes.length < 3 || evento.getUser().getIdLong() != Long.parseUnsignedLong(partes[2])) {
            evento.reply(Messages.get(locale, "descansar.noestuyo")).setEphemeral(true).queue();
            return;
        }

        if (seguir) {
            // Se quitan los botones: la decisión ya está tomada y el embed no debe poder repulsarse.
            evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "descansar.sigues"))).setComponents().queue();
            return;
        }

        // Despertar toca BD (energía + estado): se difiere la edición para no agotar los 3 s de
        // Discord, y el resultado se pinta con el mismo embed que /descansar despertar.
        evento.deferEdit().queue();
        ResultadoDespertar r = descanso.despertar(evento.getUser().getIdLong(), Instant.now());
        evento.getHook().editOriginalEmbeds(DescansoComando.embedDespertar(locale, r))
                .setComponents().queue();
    }
}
