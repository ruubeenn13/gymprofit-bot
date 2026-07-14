package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
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

/** {@code /dado <numero> <apuesta>}: aciertas el número (1-6) y cobras 5× (ficción). */
public final class DadoComando implements Comando {

    private static final String NOMBRE = "dado";

    private final ApuestaService apuestas;
    private final Cooldown cooldown;

    public DadoComando(ApuestaService apuestas, Cooldown cooldown) {
        this.apuestas = apuestas;
        this.cooldown = cooldown;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData numero = new OptionData(OptionType.INTEGER, "numero",
                Messages.get(Messages.ES, "comando.dado.opcion.numero"), true)
                .setMinValue(1).setMaxValue(6)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.dado.opcion.numero"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.dado.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.dado.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.dado.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(numero, GamblingHelper.opcionApuesta());
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (GamblingHelper.enCooldown(evento, cooldown, locale)) {
            return;
        }
        int numero = evento.getOption("numero").getAsInt();
        long apuesta = evento.getOption("apuesta").getAsLong();

        evento.deferReply(false).queue();
        Resultado r = apuestas.dado(evento.getUser().getIdLong(), apuesta, numero);
        evento.getHook().sendMessageEmbeds(GamblingHelper.embed(locale, r,
                Messages.get(locale, "dado.resultado", r.tirada(), numero))).queue();
    }
}
