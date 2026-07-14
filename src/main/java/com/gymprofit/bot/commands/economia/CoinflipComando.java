package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ApuestaService;
import com.gymprofit.bot.services.ApuestaService.Resultado;
import com.gymprofit.bot.util.Cooldown;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /coinflip <apuesta> <cara>}: cara o cruz por coins (ficción). Paga el doble. */
public final class CoinflipComando implements Comando {

    private static final String NOMBRE = "coinflip";

    private final ApuestaService apuestas;
    private final Cooldown cooldown;

    public CoinflipComando(ApuestaService apuestas, Cooldown cooldown) {
        this.apuestas = apuestas;
        this.cooldown = cooldown;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData apuesta = GamblingHelper.opcionApuesta();
        OptionData cara = new OptionData(OptionType.STRING, "cara",
                Messages.get(Messages.ES, "comando.coinflip.opcion.cara"), true)
                .addChoice("cara", "cara").addChoice("cruz", "cruz")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.coinflip.opcion.cara"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.coinflip.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.coinflip.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.coinflip.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(apuesta, cara);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (GamblingHelper.enCooldown(evento, cooldown, locale)) {
            return;
        }
        long apuesta = evento.getOption("apuesta").getAsLong();
        boolean cara = evento.getOption("cara").getAsString().equals("cara");

        evento.deferReply(false).queue();
        Resultado r = apuestas.coinflip(evento.getUser().getIdLong(), apuesta, cara);
        String salio = Messages.get(locale, r.tirada() == 1 ? "coinflip.cara" : "coinflip.cruz");
        evento.getHook().sendMessageEmbeds(GamblingHelper.embed(locale, r,
                Messages.get(locale, "coinflip.resultado", salio))).queue();
    }
}
