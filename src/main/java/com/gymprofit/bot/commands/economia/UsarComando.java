package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ItemService;
import com.gymprofit.bot.services.ItemService.ResultadoUso;
import com.gymprofit.bot.services.Items;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /usar}: consume un ítem del inventario y aplica su efecto (energía o salud). */
public final class UsarComando implements Comando {

    private static final String NOMBRE = "usar";

    private final ItemService items;

    public UsarComando(ItemService items) {
        this.items = items;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData item = new OptionData(OptionType.STRING, "item",
                Messages.get(Messages.ES, "comando.usar.opcion.item"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.usar.opcion.item"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.usar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.usar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.usar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(item);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String itemId = evento.getOption("item").getAsString();

        evento.deferReply(true).queue();
        ResultadoUso r = items.usar(evento.getUser().getIdLong(), itemId);
        String nombre = Items.porId(itemId)
                .map(i -> Messages.get(locale, "item." + i.id())).orElse(itemId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "usar.ok", nombre,
                    Messages.get(locale, "usar.efecto." + r.efecto().name().toLowerCase()), r.valor());
            case NO_EXISTE -> Messages.get(locale, "comprar.noexiste");
            case NO_CONSUMIBLE -> Messages.get(locale, "usar.noconsumible");
            case NO_TIENE -> Messages.get(locale, "usar.notiene", nombre);
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
