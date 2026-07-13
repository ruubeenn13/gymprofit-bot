package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.TrabajoService;
import com.gymprofit.bot.services.TrabajoService.ResultadoEntrenar;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /entrenar}: gasta energía para subir un atributo (fuerza, resistencia o carisma) +1. */
public final class EntrenarComando implements Comando {

    private static final String NOMBRE = "entrenar";

    private final TrabajoService trabajos;

    public EntrenarComando(TrabajoService trabajos) {
        this.trabajos = trabajos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData atributo = new OptionData(OptionType.STRING, "atributo",
                Messages.get(Messages.ES, "comando.entrenar.opcion.atributo"), true)
                .addChoice("fuerza", "fuerza")
                .addChoice("resistencia", "resistencia")
                .addChoice("carisma", "carisma")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.entrenar.opcion.atributo"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.entrenar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.entrenar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.entrenar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(atributo);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String atributo = evento.getOption("atributo").getAsString();

        evento.deferReply(false).queue();
        ResultadoEntrenar r = trabajos.entrenar(evento.getUser().getIdLong(), atributo);
        String mensaje = r == ResultadoEntrenar.OK
                ? Messages.get(locale, "entrenar.ok", Messages.get(locale, "atributo." + atributo),
                        TrabajoService.ENERGIA_ENTRENAR)
                : Messages.get(locale, "entrenar.sinenergia", TrabajoService.ENERGIA_ENTRENAR);
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
