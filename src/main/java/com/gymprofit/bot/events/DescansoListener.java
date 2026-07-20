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
 *
 * <p>Al despertar se <b>relanza la acción que quedó bloqueada</b> ({@link ReintentoRegistro}): si
 * ibas a currar, curras. Levantarse y tener que reescribir el comando era la mitad del trabajo, y
 * dejaba la sensación de que el botón no había hecho nada.
 */
public final class DescansoListener extends ListenerAdapter {

    private final DescansoService descanso;
    private final ReintentoRegistro reintentos;

    public DescansoListener(DescansoService descanso, ReintentoRegistro reintentos) {
        this.descanso = descanso;
        this.reintentos = reintentos;
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
            // Sigue durmiendo: lo que iba a hacer se descarta, o le saltaría al despertar mucho más
            // tarde sin venir a cuento.
            reintentos.descartar(evento.getUser().getIdLong());
            // Se quitan los botones: la decisión ya está tomada y el embed no debe poder repulsarse.
            evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "descansar.sigues"))).setComponents().queue();
            return;
        }

        // Despertar toca BD (energía + estado): se difiere la edición para no agotar los 3 s de
        // Discord, y el resultado se pinta con el mismo embed que /descansar despertar.
        evento.deferEdit().queue();
        long userId = evento.getUser().getIdLong();
        ResultadoDespertar r = descanso.despertar(userId, Instant.now());
        evento.getHook().editOriginalEmbeds(DescansoComando.embedDespertar(locale, r))
                .setComponents().queue();
        DescansoComando.reintentar(reintentos, userId, locale, evento.getHook());
    }
}
