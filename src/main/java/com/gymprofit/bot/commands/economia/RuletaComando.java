package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ApuestaService;
import com.gymprofit.bot.services.ApuestaService.Color;
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

/** {@code /ruleta <apuesta> <color>}: rojo/negro pagan 2×, verde 30× (ficción). */
public final class RuletaComando implements Comando {

    private static final String NOMBRE = "ruleta";

    private final ApuestaService apuestas;
    private final Cooldown cooldown;

    public RuletaComando(ApuestaService apuestas, Cooldown cooldown) {
        this.apuestas = apuestas;
        this.cooldown = cooldown;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData color = new OptionData(OptionType.STRING, "color",
                Messages.get(Messages.ES, "comando.ruleta.opcion.color"), true)
                .addChoice("🔴 rojo", "ROJO").addChoice("⚫ negro", "NEGRO").addChoice("🟢 verde", "VERDE")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ruleta.opcion.color"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.ruleta.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.ruleta.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.ruleta.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(GamblingHelper.opcionApuesta(), color);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (GamblingHelper.enCooldown(evento, cooldown, locale)) {
            return;
        }
        long apuesta = evento.getOption("apuesta").getAsLong();
        Color color = Color.valueOf(evento.getOption("color").getAsString());

        evento.deferReply(false).queue();
        Resultado r = apuestas.ruleta(evento.getUser().getIdLong(), apuesta, color);
        String salio = Messages.get(locale, "ruleta.color." + colorDe(r.tirada()));
        evento.getHook().sendMessageEmbeds(GamblingHelper.embed(locale, r,
                Messages.get(locale, "ruleta.resultado", r.tirada(), salio))).queue();
    }

    private static String colorDe(int slot) {
        return slot == 0 ? "verde" : (slot <= 18 ? "rojo" : "negro");
    }
}
