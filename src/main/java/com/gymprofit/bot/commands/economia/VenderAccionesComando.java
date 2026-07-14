package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Acciones;
import com.gymprofit.bot.services.BolsaService;
import com.gymprofit.bot.services.BolsaService.ResultadoVender;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /vender-acciones <accion> <cantidad>}: vende acciones al precio actual (con comisión). */
public final class VenderAccionesComando implements Comando {

    private static final String NOMBRE = "vender-acciones";

    private final BolsaService bolsa;

    public VenderAccionesComando(BolsaService bolsa) {
        this.bolsa = bolsa;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData accion = new OptionData(OptionType.STRING, "accion",
                Messages.get(Messages.ES, "comando.invertir.opcion.accion"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.invertir.opcion.accion"));
        for (Acciones a : Acciones.CATALOGO) {
            accion.addChoice(a.emoji() + " " + Messages.get(Messages.ES, "accion." + a.id()), a.id());
        }
        OptionData cantidad = new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.invertir.opcion.cantidad"), true)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.invertir.opcion.cantidad"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.venderacciones.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.venderacciones.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.venderacciones.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(accion, cantidad);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String accionId = evento.getOption("accion").getAsString();
        long cantidad = evento.getOption("cantidad").getAsLong();

        evento.deferReply(false).queue();
        ResultadoVender r = bolsa.vender(evento.getUser().getIdLong(), accionId, cantidad);
        String nombre = Messages.get(locale, "accion." + accionId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "venderacciones.ok", r.cantidad(), nombre, r.neto());
            case SIN_ACCIONES -> Messages.get(locale, "venderacciones.sinacciones", nombre);
            case NO_EXISTE -> Messages.get(locale, "invertir.noexiste");
            case CANTIDAD_INVALIDA -> Messages.get(locale, "vender.cantidad");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
