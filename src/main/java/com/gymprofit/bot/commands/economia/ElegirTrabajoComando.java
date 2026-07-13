package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.TrabajoService;
import com.gymprofit.bot.services.TrabajoService.ResultadoElegir;
import com.gymprofit.bot.services.Trabajos;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /elegir-trabajo}: fija tu trabajo actual (si cumples el requisito de nivel). */
public final class ElegirTrabajoComando implements Comando {

    private static final String NOMBRE = "elegir-trabajo";

    private final TrabajoService trabajos;

    public ElegirTrabajoComando(TrabajoService trabajos) {
        this.trabajos = trabajos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData trabajo = new OptionData(OptionType.STRING, "trabajo",
                Messages.get(Messages.ES, "comando.elegirtrabajo.opcion.trabajo"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.elegirtrabajo.opcion.trabajo"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.elegirtrabajo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.elegirtrabajo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.elegirtrabajo.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(trabajo);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String id = evento.getOption("trabajo").getAsString();

        evento.deferReply(true).queue();
        ResultadoElegir r = trabajos.elegir(evento.getUser().getIdLong(), id);
        String mensaje = switch (r) {
            case OK -> Messages.get(locale, "elegirtrabajo.ok", Messages.get(locale, "trabajo." + id));
            case NO_EXISTE -> Messages.get(locale, "elegirtrabajo.noexiste");
            case REQUISITO -> Messages.get(locale, "elegirtrabajo.requisito",
                    Trabajos.porId(id).map(Trabajos::requisitoNivel).orElse(0));
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
