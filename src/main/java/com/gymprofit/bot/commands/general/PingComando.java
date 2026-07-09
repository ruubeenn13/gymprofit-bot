package com.gymprofit.bot.commands.general;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /ping}: comprobación de vida del bot. Responde (efímero) con la latencia del gateway.
 * No toca BD ni API, así que no necesita cooldown ni {@code deferReply}. Sirve además de comando
 * de referencia para el resto de la Fase 1.
 */
public final class PingComando implements Comando {

    private static final String NOMBRE = "ping";

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.ping.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.ping.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ping.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_UK,
                        Messages.get(Messages.EN, "comando.ping.descripcion"));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long latencia = evento.getJDA().getGatewayPing();

        MessageEmbed embed = EmbedFactory.base(
                EmbedFactory.Tipo.STATS,
                locale,
                Messages.get(locale, "comando.ping.titulo"),
                Messages.get(locale, "comando.ping.latencia", latencia)
        ).build();

        evento.replyEmbeds(embed).setEphemeral(true).queue();
    }
}
