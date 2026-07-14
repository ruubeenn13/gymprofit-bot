package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.GremioService;
import com.gymprofit.bot.services.GremioService.Info;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/** {@code /gremio}: muestra tu gremio (dueño, miembros) o te dice que no tienes ninguno. */
public final class GremioComando implements Comando {

    private static final String NOMBRE = "gremio";

    private final GremioService gremios;

    public GremioComando(GremioService gremios) {
        this.gremios = gremios;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.gremio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.gremio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.gremio.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(true).queue();
        Optional<Info> info = gremios.info(evento.getUser().getIdLong());
        if (info.isEmpty()) {
            evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "gremio.notienes"))).queue();
            return;
        }
        Info i = info.get();
        String miembros = i.miembros().stream().map(id -> "<@" + id + ">")
                .collect(Collectors.joining(", "));
        String desc = Messages.get(locale, "gremio.cuerpo",
                "<@" + i.gremio().dueno() + ">", i.miembros().size(), GremioService.MAX_MIEMBROS,
                miembros);
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "gremio.titulo", i.gremio().nombre()), desc).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }
}
