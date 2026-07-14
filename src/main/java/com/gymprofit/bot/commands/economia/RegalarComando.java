package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.RegaloService;
import com.gymprofit.bot.services.RegaloService.Estado;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /regalar <usuario> <coins>}: transfiere coins a otro jugador. */
public final class RegalarComando implements Comando {

    private static final String NOMBRE = "regalar";

    private final RegaloService regalos;

    public RegalarComando(RegaloService regalos) {
        this.regalos = regalos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.regalar.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.regalar.opcion.usuario"));
        OptionData coins = new OptionData(OptionType.INTEGER, "coins",
                Messages.get(Messages.ES, "comando.regalar.opcion.coins"), true)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.regalar.opcion.coins"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.regalar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.regalar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.regalar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(usuario, coins);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User destino = evento.getOption("usuario").getAsUser();
        long cantidad = evento.getOption("coins").getAsLong();

        if (destino.isBot()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "regalar.bot"))).setEphemeral(true).queue();
            return;
        }

        evento.deferReply(false).queue();
        Estado r = regalos.regalarCoins(evento.getUser().getIdLong(), destino.getIdLong(), cantidad);
        String mensaje = switch (r) {
            case OK -> Messages.get(locale, "regalar.ok", cantidad, destino.getAsMention());
            case SIN_SALDO -> Messages.get(locale, "regalar.sinsaldo");
            case A_TI_MISMO -> Messages.get(locale, "regalar.atimismo");
            case CANTIDAD_INVALIDA -> Messages.get(locale, "vender.cantidad");
            default -> Messages.get(locale, "comando.error.generico");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
