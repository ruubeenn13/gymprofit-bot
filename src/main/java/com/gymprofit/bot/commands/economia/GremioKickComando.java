package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.GremioService;
import com.gymprofit.bot.services.GremioService.ResultadoMiembro;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /gremio-kick <usuario>}: el dueño expulsa a un miembro (y le quita el canal). */
public final class GremioKickComando implements Comando {

    private static final String NOMBRE = "gremio-kick";

    private final GremioService gremios;

    public GremioKickComando(GremioService gremios) {
        this.gremios = gremios;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.gremiokick.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.gremiokick.opcion.usuario"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.gremiokick.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.gremiokick.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.gremiokick.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(usuario);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User objetivo = evento.getOption("usuario").getAsUser();

        evento.deferReply(false).queue();
        ResultadoMiembro r = gremios.expulsar(evento.getUser().getIdLong(), objetivo.getIdLong());
        if (r.estado() == GremioService.EstadoMiembro.OK && evento.getGuild() != null) {
            GremioCanal.quitar(evento.getGuild(), r.gremio().canalId(), objetivo.getIdLong());
        }
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "gremio.expulsado", objetivo.getAsMention());
            case NO_ERES_DUENO -> Messages.get(locale, "gremio.noeresdueno");
            case NO_MIEMBRO -> Messages.get(locale, "gremio.nomiembro");
            case NO_PUEDES -> Messages.get(locale, "gremio.nopuedesati");
            default -> Messages.get(locale, "comando.error.generico");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
