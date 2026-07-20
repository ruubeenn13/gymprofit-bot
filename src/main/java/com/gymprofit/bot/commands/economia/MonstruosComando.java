package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.Mundos;
import com.gymprofit.bot.services.Monstruos;
import com.gymprofit.bot.services.Monstruos.Dificultad;
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

/**
 * {@code /monstruos <mundo>}: bestiario de un mundo. Muestra sus monstruos agrupados por dificultad
 * (un embed por dificultad, para no rebasar el límite de 4096 caracteres de descripción), con poder,
 * HP, recompensas y botín. Solo datos: la pelea llega en COMBAT-3.
 */
public final class MonstruosComando implements Comando {

    private static final String NOMBRE = "monstruos";

    @Override
    public SlashCommandData definicion() {
        OptionData mundo = new OptionData(OptionType.STRING, "mundo",
                Messages.get(Messages.ES, "comando.monstruos.opcion.mundo"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.monstruos.opcion.mundo"));
        // Una elección por mundo del catálogo (etiqueta en ES; el valor es el id estable).
        for (Mundos m : Mundos.CATALOGO) {
            mundo.addChoice(m.emoji() + " " + Messages.get(Messages.ES, "mundo." + m.id()), m.id());
        }

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.monstruos.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.monstruos.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.monstruos.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(mundo);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String mundoId = evento.getOption("mundo").getAsString();
        String nombreMundo = Mundos.porId(mundoId)
                .map(m -> m.emoji() + " " + Messages.get(locale, "mundo." + m.id()))
                .orElse(mundoId);

        // Un embed por dificultad; se omiten las dificultades sin monstruos en ese mundo.
        List<MessageEmbed> embeds = new ArrayList<>();
        for (Dificultad dif : Dificultad.values()) {
            List<Monstruos> mobs = Monstruos.deMundo(mundoId).stream()
                    .filter(mo -> mo.dificultad() == dif).toList();
            if (mobs.isEmpty()) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (Monstruos mo : mobs) {
                sb.append(Messages.get(locale, "bestiario.linea",
                        mo.emoji(), Messages.get(locale, "monstruo." + mo.id()),
                        mo.poder(), mo.hp(), mo.coins(), mo.xp()));
                String loot = lootResumen(mo);
                if (!loot.isEmpty()) {
                    sb.append(Messages.get(locale, "bestiario.loot", loot));
                }
                sb.append('\n');
            }
            String titulo = nombreMundo + " — "
                    + Messages.get(locale, "dificultad." + dif.name().toLowerCase());
            embeds.add(EmbedFactory.base(EmbedFactory.Tipo.DUELO, locale, titulo, sb.toString())
                    .build());
        }

        if (embeds.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.DUELO, locale,
                    Messages.get(locale, "monstruos.vacio"))).setEphemeral(true).queue();
            return;
        }
        evento.replyEmbeds(embeds).queue();
    }

    /** Botín compacto: emoji de cada ítem con su probabilidad (p. ej. «🍎 50% · 🧥 5%»). */
    private static String lootResumen(Monstruos mo) {
        List<String> partes = new ArrayList<>();
        for (Monstruos.Drop drop : mo.loot()) {
            String emoji = Items.porId(drop.itemId()).map(Items::emoji).orElse("❔");
            partes.add(emoji + " " + Math.round(drop.prob() * 100) + "%");
        }
        return String.join(" · ", partes);
    }
}
