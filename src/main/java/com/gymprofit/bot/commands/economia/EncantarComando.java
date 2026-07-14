package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EncantarService;
import com.gymprofit.bot.services.EncantarService.ResultadoEncanto;
import com.gymprofit.bot.services.EncantarService.ResultadoNivel;
import com.gymprofit.bot.services.Encantamiento;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /encantar}: mejora el arma equipada. Sin opción sube su <b>nivel</b> (+daño, coste
 * creciente); con la opción {@code efecto} aplica un <b>encantamiento</b> elemental del catálogo.
 */
public final class EncantarComando implements Comando {

    private static final String NOMBRE = "encantar";

    private final EncantarService encantar;

    public EncantarComando(EncantarService encantar) {
        this.encantar = encantar;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData efecto = new OptionData(OptionType.STRING, "efecto",
                Messages.get(Messages.ES, "comando.encantar.opcion.efecto"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.encantar.opcion.efecto"));
        for (Encantamiento e : Encantamiento.CATALOGO) {
            efecto.addChoice(e.emoji() + " " + Messages.get(Messages.ES, "encanto." + e.id()), e.id());
        }
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.encantar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.encantar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.encantar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(efecto);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        OptionMapping opcion = evento.getOption("efecto");
        long userId = evento.getUser().getIdLong();

        evento.deferReply(false).queue();
        String mensaje = opcion == null
                ? mensajeNivel(locale, encantar.subirNivel(userId))
                : mensajeEncanto(locale, encantar.aplicarEncanto(userId, opcion.getAsString()));
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }

    private static String mensajeNivel(Locale locale, ResultadoNivel r) {
        return switch (r.estado()) {
            case OK -> Messages.get(locale, "encantar.nivel.ok", r.nivelNuevo(), r.coste());
            case SIN_ARMA -> Messages.get(locale, "encantar.sinarma");
            case NIVEL_MAXIMO -> Messages.get(locale, "encantar.nivelmax", EncantarService.NIVEL_MAX);
            case SIN_SALDO -> Messages.get(locale, "encantar.sinsaldo", r.coste());
            case ENCANTO_NO_EXISTE -> Messages.get(locale, "encantar.noexiste");
        };
    }

    private static String mensajeEncanto(Locale locale, ResultadoEncanto r) {
        return switch (r.estado()) {
            case OK -> Messages.get(locale, "encantar.efecto.ok",
                    Messages.get(locale, "encanto." + r.encantoId()), r.coste());
            case SIN_ARMA -> Messages.get(locale, "encantar.sinarma");
            case ENCANTO_NO_EXISTE -> Messages.get(locale, "encantar.noexiste");
            case SIN_SALDO -> Messages.get(locale, "encantar.sinsaldo", r.coste());
            case NIVEL_MAXIMO -> Messages.get(locale, "encantar.nivelmax", EncantarService.NIVEL_MAX);
        };
    }
}
