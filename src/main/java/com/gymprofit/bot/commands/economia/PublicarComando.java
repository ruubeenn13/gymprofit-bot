package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.MercadoService;
import com.gymprofit.bot.services.MercadoService.PublicarResultado;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /publicar <item> <cantidad> <precio>}: pone ítems a la venta en el mercado (escrow). */
public final class PublicarComando implements Comando {

    private static final String NOMBRE = "publicar";

    private final MercadoService mercado;

    public PublicarComando(MercadoService mercado) {
        this.mercado = mercado;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData item = new OptionData(OptionType.STRING, "item",
                Messages.get(Messages.ES, "comando.publicar.opcion.item"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.publicar.opcion.item"));
        OptionData cantidad = new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.publicar.opcion.cantidad"), true)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.publicar.opcion.cantidad"));
        OptionData precio = new OptionData(OptionType.INTEGER, "precio",
                Messages.get(Messages.ES, "comando.publicar.opcion.precio"), true)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.publicar.opcion.precio"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.publicar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.publicar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.publicar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(item, cantidad, precio);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String itemId = evento.getOption("item").getAsString();
        int cantidad = evento.getOption("cantidad").getAsInt();
        long precio = evento.getOption("precio").getAsLong();

        evento.deferReply(false).queue();
        PublicarResultado r = mercado.publicar(evento.getUser().getIdLong(), itemId, cantidad, precio);
        String nombre = Items.porId(itemId)
                .map(i -> Messages.get(locale, "item." + i.id())).orElse(itemId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "publicar.ok", cantidad, nombre, precio, r.id());
            case NO_TIENE -> Messages.get(locale, "vender.notiene", nombre);
            case NO_EXISTE -> Messages.get(locale, "comprar.noexiste");
            case DATOS_INVALIDOS -> Messages.get(locale, "vender.cantidad");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
