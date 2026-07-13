package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ItemService;
import com.gymprofit.bot.services.Items;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;
import java.util.Map;

/** {@code /inventario}: muestra los ítems que posees, agrupados por categoría. */
public final class InventarioComando implements Comando {

    private static final String NOMBRE = "inventario";

    private final ItemService items;

    public InventarioComando(ItemService items) {
        this.items = items;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.inventario.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.inventario.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.inventario.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(true).queue();
        Map<String, Integer> inv = items.inventario(evento.getUser().getIdLong());

        String desc;
        if (inv.isEmpty()) {
            desc = Messages.get(locale, "inventario.vacio");
        } else {
            StringBuilder sb = new StringBuilder();
            Items.Categoria catActual = null;
            for (Items item : Items.CATALOGO) {
                Integer n = inv.get(item.id());
                if (n == null || n <= 0) {
                    continue;
                }
                if (item.categoria() != catActual) {
                    catActual = item.categoria();
                    sb.append("\n**").append(Messages.get(locale,
                            "tienda.cat." + catActual.name().toLowerCase())).append("**\n");
                }
                sb.append(Messages.get(locale, "inventario.linea",
                        item.emoji(), Messages.get(locale, "item." + item.id()), n)).append('\n');
            }
            desc = sb.toString();
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "inventario.titulo", evento.getUser().getName()), desc)
                .setThumbnail(evento.getUser().getEffectiveAvatarUrl()).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }
}
