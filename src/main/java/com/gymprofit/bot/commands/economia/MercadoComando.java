package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.ListadoMercado;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.MercadoService;
import com.gymprofit.bot.services.MercadoService.CompraResultado;
import com.gymprofit.bot.services.MercadoService.PublicarResultado;
import com.gymprofit.bot.services.MercadoService.RetirarEstado;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.Locale;

/**
 * {@code /mercado} con subcomandos (ver, publicar, comprar, retirar). Consolida el mercado entre
 * jugadores en un solo comando para no gastar cupo de slash commands.
 */
public final class MercadoComando implements Comando {

    private static final String NOMBRE = "mercado";

    private final MercadoService mercado;

    public MercadoComando(MercadoService mercado) {
        this.mercado = mercado;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData item = op(OptionType.STRING, "item", "comando.publicar.opcion.item", true);
        OptionData cantPub = op(OptionType.INTEGER, "cantidad", "comando.publicar.opcion.cantidad", true);
        OptionData precio = op(OptionType.INTEGER, "precio", "comando.publicar.opcion.precio", true);
        OptionData anuncioC = op(OptionType.INTEGER, "anuncio", "comando.comprarmercado.opcion.anuncio", true);
        OptionData cantCom = op(OptionType.INTEGER, "cantidad", "comando.comprarmercado.opcion.cantidad", false);
        OptionData anuncioR = op(OptionType.INTEGER, "anuncio", "comando.retirar.opcion.anuncio", true);
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.mercado.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.mercado.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.mercado.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("ver", "comando.mercado.descripcion"),
                        sub("publicar", "comando.publicar.descripcion").addOptions(item, cantPub, precio),
                        sub("comprar", "comando.comprarmercado.descripcion").addOptions(anuncioC, cantCom),
                        sub("retirar", "comando.retirar.descripcion").addOptions(anuncioR));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData op(OptionType tipo, String nombre, String claveDesc, boolean req) {
        OptionData o = new OptionData(tipo, nombre, Messages.get(Messages.ES, claveDesc), req)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
        if (tipo == OptionType.INTEGER) {
            o.setMinValue(1);
        }
        return o;
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        switch (evento.getSubcommandName() == null ? "ver" : evento.getSubcommandName()) {
            case "publicar" -> publicar(evento);
            case "comprar" -> comprar(evento);
            case "retirar" -> retirar(evento);
            default -> ver(evento);
        }
    }

    private void ver(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        List<ListadoMercado> anuncios = mercado.listar();
        String cuerpo;
        if (anuncios.isEmpty()) {
            cuerpo = Messages.get(locale, "mercado.vacio");
        } else {
            StringBuilder sb = new StringBuilder(Messages.get(locale, "mercado.intro")).append("\n\n");
            for (ListadoMercado l : anuncios) {
                String emoji = Items.porId(l.itemId()).map(Items::emoji).orElse("📦");
                sb.append(Messages.get(locale, "mercado.linea", l.id(), emoji,
                        Messages.get(locale, "item." + l.itemId()), l.cantidad(), l.precio(),
                        "<@" + l.vendedor() + ">")).append('\n');
            }
            cuerpo = sb.toString();
        }
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "mercado.titulo"), cuerpo).build()).setEphemeral(true).queue();
    }

    private void publicar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String itemId = evento.getOption("item").getAsString();
        int cantidad = evento.getOption("cantidad").getAsInt();
        long precio = evento.getOption("precio").getAsLong();
        evento.deferReply(false).queue();
        PublicarResultado r = mercado.publicar(evento.getUser().getIdLong(), itemId, cantidad, precio);
        String nombre = Items.porId(itemId).map(i -> Messages.get(locale, "item." + i.id())).orElse(itemId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "publicar.ok", cantidad, nombre, precio, r.id());
            case NO_TIENE -> Messages.get(locale, "vender.notiene", nombre);
            case NO_EXISTE -> Messages.get(locale, "comprar.noexiste");
            case DATOS_INVALIDOS -> Messages.get(locale, "vender.cantidad");
        };
        responder(evento, locale, mensaje);
    }

    private void comprar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long anuncio = evento.getOption("anuncio").getAsLong();
        OptionMapping cant = evento.getOption("cantidad");
        int cantidad = cant != null ? cant.getAsInt() : 1;
        evento.deferReply(false).queue();
        CompraResultado r = mercado.comprar(evento.getUser().getIdLong(), anuncio, cantidad);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "comprarmercado.ok", r.cantidad(),
                    Messages.get(locale, "item." + r.itemId()), r.total());
            case NO_EXISTE -> Messages.get(locale, "comprarmercado.noexiste");
            case ES_TUYO -> Messages.get(locale, "comprarmercado.estuyo");
            case SIN_STOCK -> Messages.get(locale, "comprarmercado.sinstock");
            case SIN_SALDO -> Messages.get(locale, "comprarmercado.sinsaldo");
            case CANTIDAD_INVALIDA -> Messages.get(locale, "vender.cantidad");
        };
        responder(evento, locale, mensaje);
    }

    private void retirar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long anuncio = evento.getOption("anuncio").getAsLong();
        evento.deferReply(true).queue();
        RetirarEstado r = mercado.retirar(evento.getUser().getIdLong(), anuncio);
        String mensaje = switch (r) {
            case OK -> Messages.get(locale, "retirar.ok");
            case NO_EXISTE -> Messages.get(locale, "comprarmercado.noexiste");
            case NO_TUYO -> Messages.get(locale, "retirar.notuyo");
        };
        responder(evento, locale, mensaje);
    }

    private static void responder(SlashCommandInteractionEvent evento, Locale locale, String mensaje) {
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
