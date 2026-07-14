package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.VentaService;
import com.gymprofit.bot.services.VentaService.Resultado;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /vender <item> [cantidad]}: vende ítems del inventario por coins (minerales al 100 %). */
public final class VenderComando implements Comando {

    private static final String NOMBRE = "vender";

    private final VentaService venta;

    public VenderComando(VentaService venta) {
        this.venta = venta;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData item = new OptionData(OptionType.STRING, "item",
                Messages.get(Messages.ES, "comando.vender.opcion.item"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.vender.opcion.item"));
        OptionData cantidad = new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.vender.opcion.cantidad"), false)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.vender.opcion.cantidad"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.vender.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.vender.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.vender.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(item, cantidad);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String itemId = evento.getOption("item").getAsString();
        OptionMapping cant = evento.getOption("cantidad");
        int cantidad = cant != null ? cant.getAsInt() : 1;

        evento.deferReply(false).queue();
        Resultado r = venta.vender(evento.getUser().getIdLong(), itemId, cantidad);
        String nombre = Items.porId(itemId)
                .map(i -> Messages.get(locale, "item." + i.id())).orElse(itemId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "vender.ok", cantidad, nombre, r.total());
            case NO_EXISTE -> Messages.get(locale, "comprar.noexiste");
            case NO_TIENE -> Messages.get(locale, "vender.notiene", nombre);
            case CANTIDAD_INVALIDA -> Messages.get(locale, "vender.cantidad");
            case NO_VENDIBLE -> Messages.get(locale, "vender.novendible", nombre);
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
