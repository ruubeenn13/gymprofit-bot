package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.Recetas;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /recetas}: lista las recetas de herrería (qué fabrican y qué minerales piden). */
public final class RecetasComando implements Comando {

    private static final String NOMBRE = "recetas";

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.recetas.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.recetas.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.recetas.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        StringBuilder sb = new StringBuilder(Messages.get(locale, "recetas.intro")).append("\n\n");
        for (Recetas r : Recetas.CATALOGO) {
            String emoji = Items.porId(r.resultado()).map(Items::emoji).orElse("🔨");
            sb.append(Messages.get(locale, "recetas.linea", emoji,
                    Messages.get(locale, "item." + r.resultado()),
                    CrafteoComando.listaItems(locale, r.ingredientes()))).append('\n');
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "recetas.titulo"), sb.toString()).build();
        evento.replyEmbeds(embed).setEphemeral(true).queue();
    }
}
