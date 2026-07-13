package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.CombateService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /desequipar}: libera una ranura de equipo (arma o armadura). */
public final class DesequiparComando implements Comando {

    private static final String NOMBRE = "desequipar";

    private final CombateService combate;

    public DesequiparComando(CombateService combate) {
        this.combate = combate;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData ranura = new OptionData(OptionType.STRING, "ranura",
                Messages.get(Messages.ES, "comando.desequipar.opcion.ranura"), true)
                .addChoice("arma", CombateService.RANURA_ARMA)
                .addChoice("armadura", CombateService.RANURA_ARMADURA)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.desequipar.opcion.ranura"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.desequipar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.desequipar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.desequipar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(ranura);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String ranura = evento.getOption("ranura").getAsString();

        evento.deferReply(false).queue();
        boolean liberada = combate.desequipar(evento.getUser().getIdLong(), ranura);
        String nombreRanura = Messages.get(locale, "equipar.ranura." + ranura);
        String mensaje = liberada
                ? Messages.get(locale, "desequipar.ok", nombreRanura)
                : Messages.get(locale, "desequipar.nada", nombreRanura);
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
