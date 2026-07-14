package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Mazmorras;
import com.gymprofit.bot.services.MundoService;
import com.gymprofit.bot.services.MundoService.MundoVista;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /mazmorra <mazmorra>}: propone entrar en una mazmorra (oleadas de monstruos + jefe). Valida
 * el mundo y muestra un botón «Entrar»; a partir de ahí la conduce {@code CombateListener} reusando
 * el combate por turnos.
 */
public final class MazmorraComando implements Comando {

    private static final String NOMBRE = "mazmorra";

    private final MundoService mundos;

    public MazmorraComando(MundoService mundos) {
        this.mundos = mundos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData mz = new OptionData(OptionType.STRING, "mazmorra",
                Messages.get(Messages.ES, "comando.mazmorra.opcion.mazmorra"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.mazmorra.opcion.mazmorra"));
        for (Mazmorras m : Mazmorras.CATALOGO) {
            mz.addChoice(m.emoji() + " " + Messages.get(Messages.ES, "mazmorra." + m.id()), m.id());
        }
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.mazmorra.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.mazmorra.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.mazmorra.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(mz);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Mazmorras mz = Mazmorras.porId(evento.getOption("mazmorra").getAsString()).orElse(null);
        if (mz == null) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.DUELO, locale,
                    Messages.get(locale, "monstruos.vacio"))).setEphemeral(true).queue();
            return;
        }
        long userId = evento.getUser().getIdLong();
        MundoService.Progreso progreso = mundos.progreso(userId);
        MundoVista vista = progreso.mundos().stream()
                .filter(v -> v.mundo().id().equals(mz.mundo())).findFirst().orElse(null);
        if (vista == null || !vista.desbloqueado()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.DUELO, locale,
                    Messages.get(locale, "pelear.bloqueado"))).setEphemeral(true).queue();
            return;
        }
        if (progreso.nivelJugador() < vista.mundo().nivelRequerido()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.DUELO, locale,
                    Messages.get(locale, "pelear.nivel", vista.mundo().nivelRequerido()))
            ).setEphemeral(true).queue();
            return;
        }
        evento.replyEmbeds(PelearComando.embedEntrarMazmorra(locale, mz))
                .setComponents(PelearComando.botonEntrarMazmorra(userId, mz.id(), locale))
                .queue();
    }
}
