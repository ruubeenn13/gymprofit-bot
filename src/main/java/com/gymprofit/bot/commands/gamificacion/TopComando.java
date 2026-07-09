package com.gymprofit.bot.commands.gamificacion;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.NivelCalculadora;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.Locale;

/**
 * {@code /top}: leaderboard de los usuarios con más XP del servidor. Solo lee BD.
 */
public final class TopComando implements Comando {

    private static final String NOMBRE = "top";
    private static final int LIMITE = 10;

    private final UsuarioDiscordRepositorio repositorio;

    public TopComando(UsuarioDiscordRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.top.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.top.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.top.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_UK,
                        Messages.get(Messages.EN, "comando.top.descripcion"));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        List<UsuarioDiscord> top = repositorio.listarTopPorXp(LIMITE);

        String descripcion;
        if (top.isEmpty()) {
            descripcion = Messages.get(locale, "comando.top.vacio");
        } else {
            StringBuilder sb = new StringBuilder();
            int puesto = 1;
            for (UsuarioDiscord u : top) {
                // Medalla para el podio; número para el resto.
                String posicion = switch (puesto) {
                    case 1 -> "🥇";
                    case 2 -> "🥈";
                    case 3 -> "🥉";
                    default -> "**" + puesto + ".**";
                };
                // El ID va como String para que MessageFormat no le meta separadores de miles
                // (romperían la mención <@id>).
                sb.append(Messages.get(locale, "comando.top.linea",
                        posicion, String.valueOf(u.discordId()),
                        NivelCalculadora.nivelDeXp(u.xp()), u.xp()));
                sb.append('\n');
                puesto++;
            }
            descripcion = sb.toString().strip();
        }

        var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "comando.top.titulo"), descripcion).build();

        evento.replyEmbeds(embed).queue();
    }
}
