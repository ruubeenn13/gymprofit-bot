package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.PrecioAccion;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Acciones;
import com.gymprofit.bot.services.BolsaService;
import com.gymprofit.bot.services.BolsaService.CarteraVista;
import com.gymprofit.bot.services.BolsaService.PosicionVista;
import com.gymprofit.bot.services.BolsaService.ResultadoInvertir;
import com.gymprofit.bot.services.BolsaService.ResultadoVender;
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
 * {@code /bolsa} con subcomandos (ver, invertir, vender, cartera). Consolida la bolsa ficticia en un
 * solo comando para no gastar cupo de slash commands.
 */
public final class BolsaComando implements Comando {

    private static final String NOMBRE = "bolsa";

    private final BolsaService bolsa;

    public BolsaComando(BolsaService bolsa) {
        this.bolsa = bolsa;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.bolsa.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.bolsa.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.bolsa.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("ver", "comando.bolsa.descripcion"),
                        sub("invertir", "comando.invertir.descripcion").addOptions(accion(), cantidad()),
                        sub("vender", "comando.venderacciones.descripcion").addOptions(accion(), cantidad()),
                        sub("cartera", "comando.cartera.descripcion"));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData accion() {
        OptionData a = new OptionData(OptionType.STRING, "accion",
                Messages.get(Messages.ES, "comando.invertir.opcion.accion"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.invertir.opcion.accion"));
        for (Acciones ac : Acciones.CATALOGO) {
            a.addChoice(ac.emoji() + " " + Messages.get(Messages.ES, "accion." + ac.id()), ac.id());
        }
        return a;
    }

    private static OptionData cantidad() {
        return new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.invertir.opcion.cantidad"), true).setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.invertir.opcion.cantidad"));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        switch (evento.getSubcommandName() == null ? "ver" : evento.getSubcommandName()) {
            case "invertir" -> invertir(evento);
            case "vender" -> vender(evento);
            case "cartera" -> cartera(evento);
            default -> ver(evento);
        }
    }

    private void ver(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        StringBuilder sb = new StringBuilder(Messages.get(locale, "bolsa.intro")).append("\n\n");
        for (PrecioAccion p : bolsa.precios()) {
            String emoji = Acciones.porId(p.id()).map(Acciones::emoji).orElse("📈");
            String t = p.precio() > p.previo() ? "🟢▲" : (p.precio() < p.previo() ? "🔴▼" : "⚪▬");
            sb.append(Messages.get(locale, "bolsa.linea", emoji,
                    Messages.get(locale, "accion." + p.id()), p.id().toUpperCase(Locale.ROOT),
                    p.precio(), t)).append('\n');
        }
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "bolsa.titulo"), sb.toString()).build()).setEphemeral(true).queue();
    }

    private void invertir(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String accionId = evento.getOption("accion").getAsString();
        long cantidad = evento.getOption("cantidad").getAsLong();
        evento.deferReply(false).queue();
        ResultadoInvertir r = bolsa.invertir(evento.getUser().getIdLong(), accionId, cantidad);
        String nombre = Messages.get(locale, "accion." + accionId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "invertir.ok", r.cantidad(), nombre, r.precioUnit(), r.coste());
            case SIN_SALDO -> Messages.get(locale, "invertir.sinsaldo", r.coste());
            case NO_EXISTE -> Messages.get(locale, "invertir.noexiste");
            case CANTIDAD_INVALIDA -> Messages.get(locale, "vender.cantidad");
        };
        responder(evento, locale, mensaje);
    }

    private void vender(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String accionId = evento.getOption("accion").getAsString();
        long cantidad = evento.getOption("cantidad").getAsLong();
        evento.deferReply(false).queue();
        ResultadoVender r = bolsa.vender(evento.getUser().getIdLong(), accionId, cantidad);
        String nombre = Messages.get(locale, "accion." + accionId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "venderacciones.ok", r.cantidad(), nombre, r.neto());
            case SIN_ACCIONES -> Messages.get(locale, "venderacciones.sinacciones", nombre);
            case NO_EXISTE -> Messages.get(locale, "invertir.noexiste");
            case CANTIDAD_INVALIDA -> Messages.get(locale, "vender.cantidad");
        };
        responder(evento, locale, mensaje);
    }

    private void cartera(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(true).queue();
        CarteraVista c = bolsa.cartera(evento.getUser().getIdLong());
        String cuerpo;
        if (c.posiciones().isEmpty()) {
            cuerpo = Messages.get(locale, "cartera.vacia");
        } else {
            StringBuilder sb = new StringBuilder();
            for (PosicionVista p : c.posiciones()) {
                String emoji = Acciones.porId(p.accionId()).map(Acciones::emoji).orElse("📈");
                String signo = p.pl() >= 0 ? "🟢 +" : "🔴 ";
                sb.append(Messages.get(locale, "cartera.linea", emoji,
                        Messages.get(locale, "accion." + p.accionId()), p.cantidad(), p.valor(),
                        signo + p.pl())).append('\n');
            }
            String signoTotal = c.plTotal() >= 0 ? "🟢 +" : "🔴 ";
            sb.append('\n').append(Messages.get(locale, "cartera.total", c.valorTotal(),
                    signoTotal + c.plTotal()));
            cuerpo = sb.toString();
        }
        evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "cartera.titulo"), cuerpo).build()).queue();
    }

    private static void responder(SlashCommandInteractionEvent evento, Locale locale, String mensaje) {
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
