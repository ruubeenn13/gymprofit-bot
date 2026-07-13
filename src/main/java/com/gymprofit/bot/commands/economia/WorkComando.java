package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.TrabajoService;
import com.gymprofit.bot.services.TrabajoService.ResultadoWork;
import com.gymprofit.bot.util.Duraciones;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.time.Instant;
import java.util.Locale;

/** {@code /work}: trabaja un turno en tu empleo actual. Gana coins, gasta energía, con cooldown. */
public final class WorkComando implements Comando {

    private static final String NOMBRE = "work";

    private final TrabajoService trabajos;

    public WorkComando(TrabajoService trabajos) {
        this.trabajos = trabajos;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.work.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.work.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.work.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(true).queue();
        ResultadoWork r = trabajos.trabajar(evento.getUser().getIdLong(), Instant.now());
        String desc = switch (r.estado()) {
            case OK -> Messages.get(locale, "work.ok", r.pago(), r.energiaRestante());
            case SIN_TRABAJO -> Messages.get(locale, "work.sintrabajo");
            case EN_COOLDOWN -> Messages.get(locale, "work.cooldown",
                    Duraciones.formatear(r.segundosRestantes()));
            case SIN_ENERGIA -> Messages.get(locale, "work.sinenergia");
        };
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "work.titulo"), desc).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }
}
