package com.gymprofit.bot.commands.privacidad;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /privacidad}: explica qué datos guarda el bot, para qué, cuánto tiempo y cómo ejercer los
 * derechos RGPD ({@code /mis-datos}, {@code /borrar-mis-datos}). Disponible para cualquier usuario.
 */
public final class PrivacidadComando implements Comando {

    private static final String NOMBRE = "privacidad";

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.privacidad.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.privacidad.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.privacidad.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale,
                Messages.get(locale, "privacidad.titulo"),
                Messages.get(locale, "privacidad.texto")).build();
        evento.replyEmbeds(embed).setEphemeral(true).queue();
    }
}
