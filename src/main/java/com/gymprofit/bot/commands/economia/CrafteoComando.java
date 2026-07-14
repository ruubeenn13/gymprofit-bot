package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.CrafteoService;
import com.gymprofit.bot.services.CrafteoService.Resultado;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.Recetas;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;
import java.util.stream.Collectors;

/** {@code /craftear <receta>}: fabrica un arma, armadura o pico gastando minerales del inventario. */
public final class CrafteoComando implements Comando {

    private static final String NOMBRE = "craftear";

    private final CrafteoService crafteo;

    public CrafteoComando(CrafteoService crafteo) {
        this.crafteo = crafteo;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData receta = new OptionData(OptionType.STRING, "receta",
                Messages.get(Messages.ES, "comando.craftear.opcion.receta"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.craftear.opcion.receta"));
        for (Recetas r : Recetas.CATALOGO) {
            receta.addChoice(Messages.get(Messages.ES, "item." + r.resultado()), r.resultado());
        }
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.craftear.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.craftear.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.craftear.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(receta);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String recetaId = evento.getOption("receta").getAsString();

        evento.deferReply(false).queue();
        Resultado r = crafteo.craftear(evento.getUser().getIdLong(), recetaId);
        String nombre = Messages.get(locale, "item." + recetaId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "craftear.ok", nombre);
            case NO_EXISTE -> Messages.get(locale, "craftear.noexiste");
            case FALTAN_INGREDIENTES -> Messages.get(locale, "craftear.faltan", nombre,
                    listaItems(locale, r.faltantes()));
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }

    /** Formatea una lista de ingredientes como «emoji Nombre ×N, …». */
    static String listaItems(Locale locale, java.util.List<Recetas.Ingrediente> ings) {
        return ings.stream().map(ing -> {
            String emoji = Items.porId(ing.itemId()).map(Items::emoji).orElse("•");
            return emoji + " " + Messages.get(locale, "item." + ing.itemId()) + " ×" + ing.cantidad();
        }).collect(Collectors.joining(", "));
    }
}
