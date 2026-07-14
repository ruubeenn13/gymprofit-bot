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

/** {@code /disolver-gremio}: el dueño disuelve el gremio y borra su canal. */
public final class DisolverGremioComando implements Comando {

    private static final String NOMBRE = "disolver-gremio";

    private final GremioService gremios;

    public DisolverGremioComando(GremioService gremios) {
        this.gremios = gremios;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.disolvergremio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.disolvergremio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.disolvergremio.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());

        evento.deferReply(false).queue();
        ResultadoMiembro r = gremios.disolver(evento.getUser().getIdLong());
        if (r.estado() == GremioService.EstadoMiembro.OK && evento.getGuild() != null) {
            GremioCanal.eliminar(evento.getGuild(), r.gremio().canalId());
        }
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "gremio.disuelto");
            case NO_TIENES -> Messages.get(locale, "gremio.notienes");
            case NO_ERES_DUENO -> Messages.get(locale, "gremio.noeresdueno");
            default -> Messages.get(locale, "comando.error.generico");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
