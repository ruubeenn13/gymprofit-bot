package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ApuestaService;
import com.gymprofit.bot.services.DueloService;
import com.gymprofit.bot.services.DueloService.Duelo;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /duelo <usuario> <apuesta>}: reta a otro jugador a un duelo de apuesta (lo confirma con botón). */
public final class DueloComando implements Comando {

    private static final String NOMBRE = "duelo";

    private final DueloService duelos;

    public DueloComando(DueloService duelos) {
        this.duelos = duelos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.duelo.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.duelo.opcion.usuario"));
        OptionData apuesta = new OptionData(OptionType.INTEGER, "apuesta",
                Messages.get(Messages.ES, "comando.apuesta.opcion.apuesta"), true)
                .setMinValue(ApuestaService.APUESTA_MIN).setMaxValue(ApuestaService.APUESTA_MAX);
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.duelo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.duelo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.duelo.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(usuario, apuesta);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User retado = evento.getOption("usuario").getAsUser();
        long apuesta = evento.getOption("apuesta").getAsLong();
        long retador = evento.getUser().getIdLong();

        if (retado.isBot() || retado.getIdLong() == retador) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "duelo.destino"))).setEphemeral(true).queue();
            return;
        }
        long id = duelos.proponer(new Duelo(retador, retado.getIdLong(), apuesta));
        var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "duelo.titulo"),
                Messages.get(locale, "duelo.reto", "<@" + retador + ">", retado.getAsMention(),
                        apuesta)).build();
        evento.replyEmbeds(embed).setComponents(ActionRow.of(
                        Button.success("duelo:acc:" + retado.getIdLong() + ":" + id,
                                Messages.get(locale, "duelo.aceptar")),
                        Button.danger("duelo:rej:" + retado.getIdLong() + ":" + id,
                                Messages.get(locale, "duelo.rechazar"))))
                .queue();
    }
}
