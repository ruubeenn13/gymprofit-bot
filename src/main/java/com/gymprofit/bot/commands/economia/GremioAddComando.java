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

/** {@code /gremio-add <usuario>}: el dueño añade a un jugador a su gremio (y a su canal). */
public final class GremioAddComando implements Comando {

    private static final String NOMBRE = "gremio-add";

    private final GremioService gremios;

    public GremioAddComando(GremioService gremios) {
        this.gremios = gremios;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.gremioadd.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.gremioadd.opcion.usuario"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.gremioadd.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.gremioadd.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.gremioadd.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(usuario);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User objetivo = evento.getOption("usuario").getAsUser();
        if (objetivo.isBot()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "regalar.bot"))).setEphemeral(true).queue();
            return;
        }

        evento.deferReply(false).queue();
        ResultadoMiembro r = gremios.anadir(evento.getUser().getIdLong(), objetivo.getIdLong());
        if (r.estado() == GremioService.EstadoMiembro.OK && evento.getGuild() != null) {
            GremioCanal.anadir(evento.getGuild(), r.gremio().canalId(), objetivo.getIdLong());
        }
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "gremio.anadido", objetivo.getAsMention());
            case NO_ERES_DUENO -> Messages.get(locale, "gremio.noeresdueno");
            case OBJETIVO_EN_GREMIO -> Messages.get(locale, "gremio.objetivoengremio");
            case LLENO -> Messages.get(locale, "gremio.lleno", GremioService.MAX_MIEMBROS);
            default -> Messages.get(locale, "comando.error.generico");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
