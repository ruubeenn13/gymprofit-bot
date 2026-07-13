package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.CombateService;
import com.gymprofit.bot.services.CombateService.ResultadoEquipar;
import com.gymprofit.bot.services.Items;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /equipar}: equipa un arma o armadura del inventario (suma al poder de combate). */
public final class EquiparComando implements Comando {

    private static final String NOMBRE = "equipar";

    private final CombateService combate;

    public EquiparComando(CombateService combate) {
        this.combate = combate;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData item = new OptionData(OptionType.STRING, "item",
                Messages.get(Messages.ES, "comando.equipar.opcion.item"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.equipar.opcion.item"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.equipar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.equipar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.equipar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(item);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String itemId = evento.getOption("item").getAsString();

        evento.deferReply(false).queue();
        ResultadoEquipar r = combate.equipar(evento.getUser().getIdLong(), itemId);
        String nombre = Items.porId(itemId)
                .map(i -> Messages.get(locale, "item." + i.id())).orElse(itemId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "equipar.ok",
                    nombre, Messages.get(locale, "equipar.ranura." + r.ranura()), r.valor());
            case NO_EXISTE -> Messages.get(locale, "comprar.noexiste");
            case NO_EQUIPABLE -> Messages.get(locale, "equipar.noequipable");
            case NO_TIENE -> Messages.get(locale, "equipar.notiene", nombre);
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
