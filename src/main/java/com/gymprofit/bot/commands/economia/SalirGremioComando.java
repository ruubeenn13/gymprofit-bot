package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.GremioService;
import com.gymprofit.bot.services.GremioService.ResultadoMiembro;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /salir-gremio}: abandona tu gremio (si no eres el dueño). */
public final class SalirGremioComando implements Comando {

    private static final String NOMBRE = "salir-gremio";

    private final GremioService gremios;

    public SalirGremioComando(GremioService gremios) {
        this.gremios = gremios;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.salirgremio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.salirgremio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.salirgremio.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long usuario = evento.getUser().getIdLong();

        evento.deferReply(true).queue();
        ResultadoMiembro r = gremios.salir(usuario);
        if (r.estado() == GremioService.EstadoMiembro.OK && evento.getGuild() != null) {
            GremioCanal.quitar(evento.getGuild(), r.gremio().canalId(), usuario);
        }
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "gremio.saliste");
            case NO_TIENES -> Messages.get(locale, "gremio.notienes");
            case ERES_DUENO -> Messages.get(locale, "gremio.eresdueno");
            default -> Messages.get(locale, "comando.error.generico");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
