package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.RoboService;
import com.gymprofit.bot.services.RoboService.Resultado;
import com.gymprofit.bot.util.Cooldown;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /robar <usuario>}: intenta robar coins a otro jugador (con riesgo y cooldown). */
public final class RobarComando implements Comando {

    private static final String NOMBRE = "robar";

    private final RoboService robos;
    private final Cooldown cooldown;

    public RobarComando(RoboService robos, Cooldown cooldown) {
        this.robos = robos;
        this.cooldown = cooldown;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.robar.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.robar.opcion.usuario"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.robar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.robar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.robar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(usuario);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User victima = evento.getOption("usuario").getAsUser();
        long ladron = evento.getUser().getIdLong();

        if (victima.isBot() || victima.getIdLong() == ladron) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "robar.destino"))).setEphemeral(true).queue();
            return;
        }
        if (!cooldown.intentar(ladron, System.currentTimeMillis())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "robar.cooldown"))).setEphemeral(true).queue();
            return;
        }

        evento.deferReply(false).queue();
        Resultado r = robos.robar(ladron, victima.getIdLong());
        String mensaje = switch (r.estado()) {
            case EXITO -> Messages.get(locale, "robar.exito", r.cantidad(), victima.getAsMention());
            case FALLO -> Messages.get(locale, "robar.fallo", victima.getAsMention(), r.cantidad());
            case VICTIMA_SIN_SALDO -> Messages.get(locale, "robar.victimasinsaldo",
                    victima.getAsMention());
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
