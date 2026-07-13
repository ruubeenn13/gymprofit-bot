package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.EconomiaRepositorio.ResultadoDaily;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EconomiaService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /daily}: cobra tu recompensa diaria de coins y sube tu racha. */
public final class DailyComando implements Comando {

    private static final String NOMBRE = "daily";

    private final EconomiaService economia;

    public DailyComando(EconomiaService economia) {
        this.economia = economia;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.daily.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.daily.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.daily.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(false).queue();
        ResultadoDaily r = economia.daily(evento.getUser().getIdLong());
        String desc = r.cobrado()
                ? Messages.get(locale, "daily.cobrado", r.recompensa(), r.racha())
                : Messages.get(locale, "daily.yacobrado");
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "daily.titulo"), desc).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }
}
