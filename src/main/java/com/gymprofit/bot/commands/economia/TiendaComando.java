package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /tienda}: muestra el catálogo de ítems (opcionalmente filtrado por categoría). */
public final class TiendaComando implements Comando {

    private static final String NOMBRE = "tienda";

    @Override
    public SlashCommandData definicion() {
        OptionData categoria = new OptionData(OptionType.STRING, "categoria",
                Messages.get(Messages.ES, "comando.tienda.opcion.categoria"), false)
                .addChoice("consumibles", "CONSUMIBLE")
                .addChoice("equipo", "EQUIPO")
                .addChoice("bienes", "BIEN")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.tienda.opcion.categoria"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.tienda.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.tienda.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.tienda.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(categoria);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String filtro = evento.getOption("categoria") != null
                ? evento.getOption("categoria").getAsString() : null;

        StringBuilder sb = new StringBuilder();
        Items.Categoria catActual = null;
        for (Items item : Items.CATALOGO) {
            if (filtro != null && !item.categoria().name().equals(filtro)) {
                continue;
            }
            if (item.categoria() != catActual) {
                catActual = item.categoria();
                sb.append("\n**").append(Messages.get(locale,
                        "tienda.cat." + catActual.name().toLowerCase())).append("**\n");
            }
            sb.append(Messages.get(locale, "tienda.linea",
                    item.emoji(), item.id(), Messages.get(locale, "item." + item.id()),
                    item.precio())).append('\n');
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "tienda.titulo"),
                Messages.get(locale, "tienda.intro") + "\n" + sb).build();
        evento.replyEmbeds(embed).setEphemeral(true).queue();
    }
}
