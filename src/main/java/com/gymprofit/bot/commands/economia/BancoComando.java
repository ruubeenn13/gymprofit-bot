package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.BancoService;
import com.gymprofit.bot.services.BancoService.Resultado;
import com.gymprofit.bot.services.BancoService.Vista;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.Locale;

/**
 * {@code /banco} con subcomandos (ver, depositar, retirar, prestamo, pagar). Consolida el banco en un
 * solo comando para no gastar cupo de slash commands.
 */
public final class BancoComando implements Comando {

    private static final String NOMBRE = "banco";

    private final BancoService banco;

    public BancoComando(BancoService banco) {
        this.banco = banco;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.banco.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.banco.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.banco.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("ver", "comando.banco.descripcion"),
                        sub("depositar", "comando.depositar.descripcion").addOptions(coins()),
                        sub("retirar", "comando.retirarbanco.descripcion").addOptions(coins()),
                        sub("prestamo", "comando.prestamo.descripcion").addOptions(coins()),
                        sub("pagar", "comando.pagarprestamo.descripcion").addOptions(coins()));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData coins() {
        return new OptionData(OptionType.INTEGER, "coins",
                Messages.get(Messages.ES, "comando.banco.opcion.coins"), true).setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.banco.opcion.coins"));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long id = evento.getUser().getIdLong();
        String sub = evento.getSubcommandName() == null ? "ver" : evento.getSubcommandName();
        if (sub.equals("ver")) {
            evento.deferReply(true).queue();
            Vista v = banco.ver(id);
            evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "banco.titulo"),
                    Messages.get(locale, "banco.cuerpo", v.saldoBanco(), v.deuda(), v.monedero()))
                    .build()).queue();
            return;
        }
        long cantidad = evento.getOption("coins").getAsLong();
        evento.deferReply(false).queue();
        String mensaje = switch (sub) {
            case "depositar" -> mensajeDeposito(locale, banco.depositar(id, cantidad));
            case "retirar" -> mensajeRetiro(locale, banco.retirar(id, cantidad));
            case "prestamo" -> mensajePrestamo(locale, banco.prestamo(id, cantidad), cantidad);
            case "pagar" -> mensajePago(locale, banco.pagar(id, cantidad));
            default -> Messages.get(locale, "comando.error.generico");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }

    private static String mensajeDeposito(Locale locale, Resultado r) {
        return switch (r.estado()) {
            case OK -> Messages.get(locale, "banco.depositado", r.valor());
            case SIN_SALDO -> Messages.get(locale, "regalar.sinsaldo");
            default -> Messages.get(locale, "vender.cantidad");
        };
    }

    private static String mensajeRetiro(Locale locale, Resultado r) {
        return switch (r.estado()) {
            case OK -> Messages.get(locale, "banco.retirado", r.valor());
            case SIN_FONDOS -> Messages.get(locale, "banco.sinfondos");
            default -> Messages.get(locale, "vender.cantidad");
        };
    }

    private static String mensajePrestamo(Locale locale, Resultado r, long pedido) {
        return switch (r.estado()) {
            case OK -> Messages.get(locale, "banco.prestamo.ok", pedido, r.valor());
            case YA_DEUDA -> Messages.get(locale, "banco.prestamo.yadeuda", r.valor());
            case SUPERA_LIMITE -> Messages.get(locale, "banco.prestamo.limite", r.valor());
            default -> Messages.get(locale, "vender.cantidad");
        };
    }

    private static String mensajePago(Locale locale, Resultado r) {
        return switch (r.estado()) {
            case OK -> Messages.get(locale, "banco.pagado", r.valor());
            case SIN_DEUDA -> Messages.get(locale, "banco.sindeuda");
            case SIN_SALDO -> Messages.get(locale, "regalar.sinsaldo");
            default -> Messages.get(locale, "vender.cantidad");
        };
    }
}
