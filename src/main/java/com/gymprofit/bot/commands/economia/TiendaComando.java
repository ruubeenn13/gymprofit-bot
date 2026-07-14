package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.List;
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
                .addChoice("armas", "ARMA")
                .addChoice("armaduras", "ARMADURA")
                .addChoice("picos", "PICO")
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

        // Un embed por categoría: el catálogo completo excede el límite de 4096 caracteres de la
        // descripción de un embed, así que se reparte (Discord admite varios embeds por mensaje).
        List<MessageEmbed> embeds = new ArrayList<>();
        for (Items.Categoria cat : Items.Categoria.values()) {
            // Los minerales no se compran (solo se minan y se venden): no salen en la tienda.
            if (cat == Items.Categoria.MINERAL) {
                continue;
            }
            if (filtro != null && !cat.name().equals(filtro)) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (Items item : Items.CATALOGO) {
                if (item.categoria() != cat) {
                    continue;
                }
                sb.append(Messages.get(locale, "tienda.linea", item.emoji(), item.id(),
                        Messages.get(locale, "item." + item.id()), item.precio()));
                // Las armas/armaduras muestran su stat de combate junto al precio.
                if (item.categoria() == Items.Categoria.ARMA) {
                    sb.append(Messages.get(locale, "tienda.stat.ataque", item.ataque()));
                } else if (item.categoria() == Items.Categoria.ARMADURA) {
                    sb.append(Messages.get(locale, "tienda.stat.defensa", item.defensa()));
                }
                sb.append('\n');
            }
            String titulo = Messages.get(locale, "tienda.titulo") + " — "
                    + Messages.get(locale, "tienda.cat." + cat.name().toLowerCase());
            String desc = (embeds.isEmpty() ? Messages.get(locale, "tienda.intro") + "\n\n" : "")
                    + sb;
            embeds.add(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale, titulo, desc).build());
        }
        evento.replyEmbeds(embeds).setEphemeral(true).queue();
    }
}
