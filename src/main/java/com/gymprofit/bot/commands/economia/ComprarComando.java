package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ItemService;
import com.gymprofit.bot.services.ItemService.ResultadoCompra;
import com.gymprofit.bot.services.Items;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /comprar}: compra un ítem de la tienda pagando con tus coins. */
public final class ComprarComando implements Comando {

    private static final String NOMBRE = "comprar";
    private static final int MAX_CANTIDAD = 99;

    private final ItemService items;

    public ComprarComando(ItemService items) {
        this.items = items;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData item = new OptionData(OptionType.STRING, "item",
                Messages.get(Messages.ES, "comando.comprar.opcion.item"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.comprar.opcion.item"));
        OptionData cantidad = new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.comprar.opcion.cantidad"), false)
                .setRequiredRange(1, MAX_CANTIDAD)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.comprar.opcion.cantidad"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.comprar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.comprar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.comprar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(item, cantidad);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String itemId = evento.getOption("item").getAsString();
        int cantidad = evento.getOption("cantidad") != null
                ? evento.getOption("cantidad").getAsInt() : 1;

        evento.deferReply(true).queue();
        ResultadoCompra r = items.comprar(evento.getUser().getIdLong(), itemId, cantidad);
        String nombre = Items.porId(itemId)
                .map(i -> Messages.get(locale, "item." + i.id())).orElse(itemId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "comprar.ok", cantidad, nombre, r.coste());
            case NO_EXISTE -> Messages.get(locale, "comprar.noexiste");
            case SIN_SALDO -> Messages.get(locale, "comprar.sinsaldo", r.coste());
            case CANTIDAD_INVALIDA -> Messages.get(locale, "comprar.cantidad");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
