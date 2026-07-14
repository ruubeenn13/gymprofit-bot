package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.MundoService;
import com.gymprofit.bot.services.MundoService.MundoVista;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /mundos}: lista los mundos del RPG de combate con su estado para el jugador
 * (🔓 desbloqueado / 🔒 bloqueado / ✅ completado), nivel recomendado y número de monstruos.
 */
public final class MundosComando implements Comando {

    private static final String NOMBRE = "mundos";

    private final MundoService mundos;

    public MundosComando(MundoService mundos) {
        this.mundos = mundos;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.mundos.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.mundos.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.mundos.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());

        evento.deferReply(false).queue();
        MundoService.Progreso progreso = mundos.progreso(evento.getUser().getIdLong());

        StringBuilder sb = new StringBuilder(Messages.get(locale, "mundos.intro")).append("\n\n");
        for (MundoVista v : progreso.mundos()) {
            // Estado: ✅ si ya lo completó, 🔓 si está abierto, 🔒 si aún bloqueado.
            String estado = v.completado() ? "✅" : (v.desbloqueado() ? "🔓" : "🔒");
            int mobs = mundos.bestiario(v.mundo().id()).size();
            String nombre = Messages.get(locale, "mundo." + v.mundo().id());
            sb.append(Messages.get(locale, "mundos.linea",
                    estado, v.mundo().emoji(), v.mundo().id(), nombre,
                    v.mundo().nivelRequerido(), mobs)).append('\n');
        }
        sb.append('\n').append(Messages.get(locale, "mundos.pie", progreso.nivelJugador()));

        var embed = EmbedFactory.base(EmbedFactory.Tipo.DUELO, locale,
                Messages.get(locale, "mundos.titulo"), sb.toString()).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }
}
