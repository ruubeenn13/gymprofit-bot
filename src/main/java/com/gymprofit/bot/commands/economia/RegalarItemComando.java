package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.RegaloService;
import com.gymprofit.bot.services.RegaloService.Estado;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /regalar-item <usuario> <item> [cantidad]}: transfiere ítems del inventario a otro jugador. */
public final class RegalarItemComando implements Comando {

    private static final String NOMBRE = "regalar-item";

    private final RegaloService regalos;

    public RegalarItemComando(RegaloService regalos) {
        this.regalos = regalos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.regalar.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.regalar.opcion.usuario"));
        OptionData item = new OptionData(OptionType.STRING, "item",
                Messages.get(Messages.ES, "comando.regalaritem.opcion.item"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.regalaritem.opcion.item"));
        OptionData cantidad = new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.regalaritem.opcion.cantidad"), false)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.regalaritem.opcion.cantidad"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.regalaritem.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.regalaritem.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.regalaritem.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(usuario, item, cantidad);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User destino = evento.getOption("usuario").getAsUser();
        String itemId = evento.getOption("item").getAsString();
        OptionMapping cant = evento.getOption("cantidad");
        int cantidad = cant != null ? cant.getAsInt() : 1;

        if (destino.isBot()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "regalar.bot"))).setEphemeral(true).queue();
            return;
        }

        evento.deferReply(false).queue();
        Estado r = regalos.regalarItem(evento.getUser().getIdLong(), destino.getIdLong(),
                itemId, cantidad);
        String nombre = Items.porId(itemId)
                .map(i -> Messages.get(locale, "item." + i.id())).orElse(itemId);
        String mensaje = switch (r) {
            case OK -> Messages.get(locale, "regalaritem.ok", cantidad, nombre, destino.getAsMention());
            case NO_TIENE -> Messages.get(locale, "vender.notiene", nombre);
            case NO_EXISTE -> Messages.get(locale, "comprar.noexiste");
            case A_TI_MISMO -> Messages.get(locale, "regalar.atimismo");
            case CANTIDAD_INVALIDA -> Messages.get(locale, "vender.cantidad");
            default -> Messages.get(locale, "comando.error.generico");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
