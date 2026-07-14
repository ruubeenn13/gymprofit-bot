package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.BancoService;
import com.gymprofit.bot.services.BancoService.Resultado;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /pagar-prestamo <coins>}: paga (parte de) la deuda del préstamo con el monedero. */
public final class PagarPrestamoComando implements Comando {

    private static final String NOMBRE = "pagar-prestamo";

    private final BancoService banco;

    public PagarPrestamoComando(BancoService banco) {
        this.banco = banco;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData coins = new OptionData(OptionType.INTEGER, "coins",
                Messages.get(Messages.ES, "comando.banco.opcion.coins"), true)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.banco.opcion.coins"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.pagarprestamo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.pagarprestamo.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.pagarprestamo.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(coins);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long cantidad = evento.getOption("coins").getAsLong();
        evento.deferReply(false).queue();
        Resultado r = banco.pagar(evento.getUser().getIdLong(), cantidad);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "banco.pagado", r.valor());
            case SIN_DEUDA -> Messages.get(locale, "banco.sindeuda");
            case SIN_SALDO -> Messages.get(locale, "regalar.sinsaldo");
            default -> Messages.get(locale, "vender.cantidad");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
