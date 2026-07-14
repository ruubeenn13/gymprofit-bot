package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.MineriaService;
import com.gymprofit.bot.services.MineriaService.ResultadoReparar;
import com.gymprofit.bot.services.Picos;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /reparar <pico>}: restaura la durabilidad de un pico (cuesta coins según el desgaste). */
public final class RepararComando implements Comando {

    private static final String NOMBRE = "reparar";

    private final MineriaService mineria;

    public RepararComando(MineriaService mineria) {
        this.mineria = mineria;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData pico = new OptionData(OptionType.STRING, "pico",
                Messages.get(Messages.ES, "comando.reparar.opcion.pico"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.reparar.opcion.pico"));
        for (Picos p : Picos.CATALOGO) {
            pico.addChoice(Messages.get(Messages.ES, "item." + p.itemId()), p.itemId());
        }
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.reparar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.reparar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.reparar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(pico);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String picoId = evento.getOption("pico").getAsString();

        evento.deferReply(false).queue();
        ResultadoReparar r = mineria.reparar(evento.getUser().getIdLong(), picoId);
        String nombre = Messages.get(locale, "item." + picoId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "reparar.ok", nombre, r.coste());
            case NO_ES_PICO -> Messages.get(locale, "reparar.noespico");
            case NO_TIENE -> Messages.get(locale, "reparar.notiene", nombre);
            case YA_REPARADO -> Messages.get(locale, "reparar.yareparado", nombre);
            case SIN_SALDO -> Messages.get(locale, "reparar.sinsaldo", r.coste());
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
