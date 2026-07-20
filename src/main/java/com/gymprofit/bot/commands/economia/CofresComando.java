package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Cofres;
import com.gymprofit.bot.services.Cofres.Premio;
import com.gymprofit.bot.services.Items;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /cofres}: lista los cofres, su precio y las probabilidades de cada premio (transparencia). */
public final class CofresComando implements Comando {

    private static final String NOMBRE = "cofres";

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.cofres.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.cofres.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.cofres.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());

        // Un embed por cofre (las tablas con % pueden ser largas).
        List<MessageEmbed> embeds = new ArrayList<>();
        for (Cofres c : Cofres.CATALOGO) {
            Items item = Items.porId(c.itemId()).orElseThrow();
            int total = c.pesoTotal();
            StringBuilder sb = new StringBuilder(
                    Messages.get(locale, "cofres.precio", item.precio())).append("\n\n");
            for (Premio p : c.tabla()) {
                double pct = 100.0 * p.peso() / total;
                sb.append(p.rareza().emoji()).append(' ')
                        .append(describir(locale, p)).append(" — ")
                        .append(String.format(Locale.US, "%.1f%%", pct)).append('\n');
            }
            String titulo = item.emoji() + " " + Messages.get(locale, "item." + c.itemId());
            embeds.add(EmbedFactory.base(EmbedFactory.Tipo.LOGRO, locale, titulo, sb.toString())
                    .build());
        }
        evento.replyEmbeds(embeds).queue();
    }

    /** Describe un premio de la tabla (sin cantidad concreta: muestra el rango). */
    private static String describir(Locale locale, Premio p) {
        return switch (p.tipo()) {
            case COINS -> Messages.get(locale, "cofres.premio.coins", p.min(), p.max());
            case ITEM -> {
                Items i = Items.porId(p.ref()).orElse(null);
                String emoji = i != null ? i.emoji() : "🎁";
                String rango = p.max() > p.min() ? " ×" + p.min() + "-" + p.max()
                        : (p.min() > 1 ? " ×" + p.min() : "");
                yield emoji + " " + Messages.get(locale, "item." + p.ref()) + rango;
            }
            case ENCANTO -> "✨ " + Messages.get(locale, "encanto." + p.ref());
            case NIVEL -> Messages.get(locale, "cofres.premio.nivel");
        };
    }
}
