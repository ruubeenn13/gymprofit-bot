package com.gymprofit.bot.commands.comunidad;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EventoService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /reto}: fija el reto de la semana (solo staff con «Gestionar servidor»). Lo muestra el
 * contador «🎯 Reto» de la categoría SERVER STATS, que el job de estadísticas refresca.
 */
public final class RetoComando implements Comando {

    private static final String NOMBRE = "reto";

    private final EventoService eventos;

    public RetoComando(EventoService eventos) {
        this.eventos = eventos;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData texto = new OptionData(OptionType.STRING, "texto",
                Messages.get(Messages.ES, "comando.reto.opcion.texto"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.reto.opcion.texto"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.reto.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.reto.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.reto.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOptions(texto);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String texto = evento.getOption("texto").getAsString();

        evento.deferReply(true).queue();
        eventos.fijarReto(evento.getGuild().getIdLong(), texto);

        var embed = EmbedFactory.base(EmbedFactory.Tipo.RETO, locale,
                Messages.get(locale, "reto.fijado.titulo"),
                Messages.get(locale, "reto.fijado.desc", texto)).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }
}
