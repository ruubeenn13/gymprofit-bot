package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.moderacion.ModHelper;
import com.gymprofit.bot.commands.moderacion.ModlogsComando;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ModeracionService;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Locale;

/**
 * Maneja los botones ◀ ▶ del historial de {@code /modlogs} (customId {@code modlogs:<userId>:<page>}):
 * recalcula la página pedida y edita el mensaje. Solo altos cargos (defensa extra: el mensaje es
 * efímero, solo lo ve quien lo pidió).
 */
public final class ModlogsPaginadorListener extends ListenerAdapter {

    private final ModeracionService moderacion;

    public ModlogsPaginadorListener(ModeracionService moderacion) {
        this.moderacion = moderacion;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        if (!id.startsWith(ModlogsComando.PREFIJO_BOTON) || evento.getGuild() == null) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (!ModHelper.esAltoCargo(evento.getMember())) {
            evento.reply(Messages.get(locale, "mod.noautorizado")).setEphemeral(true).queue();
            return;
        }
        String[] partes = id.split(":");
        long usuarioId = Long.parseUnsignedLong(partes[1]);
        int pagina = Integer.parseInt(partes[2]);
        long guildId = evento.getGuild().getIdLong();

        evento.getJDA().retrieveUserById(usuarioId).queue(
                usuario -> editar(evento, locale, usuario.getName(), guildId, usuarioId, pagina),
                error -> editar(evento, locale, String.valueOf(usuarioId), guildId, usuarioId, pagina));
    }

    private void editar(ButtonInteractionEvent evento, Locale locale, String nombre,
                        long guildId, long usuarioId, int pagina) {
        int total = moderacion.contarSanciones(guildId, usuarioId);
        MessageEmbed embed = ModlogsComando.construirEmbed(moderacion, locale, nombre,
                guildId, usuarioId, pagina, total);
        evento.editMessageEmbeds(embed)
                .setComponents(ModlogsComando.construirBotones(usuarioId, pagina, total))
                .queue();
    }
}
