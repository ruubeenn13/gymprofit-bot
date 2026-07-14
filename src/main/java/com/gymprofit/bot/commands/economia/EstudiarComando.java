package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.TrabajoService;
import com.gymprofit.bot.services.TrabajoService.ResultadoEntrenar;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /estudiar}: gasta energía para subir estudios (+1), que dan bono al sueldo de /work. */
public final class EstudiarComando implements Comando {

    private static final String NOMBRE = "estudiar";

    private final TrabajoService trabajos;

    public EstudiarComando(TrabajoService trabajos) {
        this.trabajos = trabajos;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.estudiar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.estudiar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.estudiar.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(false).queue();
        ResultadoEntrenar r = trabajos.entrenar(evento.getUser().getIdLong(), "estudios");
        String mensaje = r == ResultadoEntrenar.OK
                ? Messages.get(locale, "estudiar.ok", TrabajoService.ENERGIA_ENTRENAR)
                : Messages.get(locale, "entrenar.sinenergia", TrabajoService.ENERGIA_ENTRENAR);
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
