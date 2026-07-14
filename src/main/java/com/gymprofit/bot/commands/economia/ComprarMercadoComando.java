package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.MercadoService;
import com.gymprofit.bot.services.MercadoService.CompraResultado;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /comprar-mercado <id> [cantidad]}: compra unidades de un anuncio del mercado. */
public final class ComprarMercadoComando implements Comando {

    private static final String NOMBRE = "comprar-mercado";

    private final MercadoService mercado;

    public ComprarMercadoComando(MercadoService mercado) {
        this.mercado = mercado;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData id = new OptionData(OptionType.INTEGER, "anuncio",
                Messages.get(Messages.ES, "comando.comprarmercado.opcion.anuncio"), true)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.comprarmercado.opcion.anuncio"));
        OptionData cantidad = new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.comprarmercado.opcion.cantidad"), false)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.comprarmercado.opcion.cantidad"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.comprarmercado.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.comprarmercado.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.comprarmercado.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(id, cantidad);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
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
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
