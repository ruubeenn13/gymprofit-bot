package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Trabajos;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /trabajos}: lista el catálogo de trabajos agrupado por tier, con sueldo y requisito. */
public final class TrabajosComando implements Comando {

    private static final String NOMBRE = "trabajos";

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.trabajos.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.trabajos.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.trabajos.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        StringBuilder sb = new StringBuilder();
        int tierActual = 0;
        for (Trabajos t : Trabajos.CATALOGO) {
            if (t.tier() != tierActual) {
                tierActual = t.tier();
                sb.append("\n**").append(Messages.get(locale, "trabajos.tier", tierActual))
                        .append("**\n");
            }
            sb.append(Messages.get(locale, "trabajos.linea",
                    t.id(), Messages.get(locale, "trabajo." + t.id()), t.sector(),
                    t.salarioMin(), t.salarioMax(), t.requisitoNivel())).append('\n');
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "trabajos.titulo"),
                Messages.get(locale, "trabajos.intro") + "\n" + sb).build();
        evento.replyEmbeds(embed).setEphemeral(true).queue();
    }
}
