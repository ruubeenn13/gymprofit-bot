package com.gymprofit.bot.events;

import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.XpResultado;
import com.gymprofit.bot.services.XpService;
import com.gymprofit.bot.util.Cooldown;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Otorga XP por participar en el chat: cada mensaje de un usuario (no bot, en un servidor) suma
 * una cantidad aleatoria de XP, con un cooldown anti-spam de 60&nbsp;s por usuario (SPEC §5 F1).
 * Cuando el usuario sube de nivel, publica un embed de celebración en el mismo canal.
 */
public final class XpMensajeListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(XpMensajeListener.class);

    /** Rango de XP por mensaje elegible (inclusive). */
    private static final int XP_MIN = 15;
    private static final int XP_MAX = 25;

    private final XpService xpService;
    private final Cooldown cooldown = new Cooldown(Duration.ofSeconds(60));

    public XpMensajeListener(XpService xpService) {
        this.xpService = xpService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent evento) {
        // Solo cuentan mensajes de personas en servidores; se ignoran bots, webhooks y DMs.
        if (!evento.isFromGuild() || evento.getAuthor().isBot() || evento.isWebhookMessage()) {
            return;
        }

        long usuarioId = evento.getAuthor().getIdLong();
        if (!cooldown.intentar(usuarioId, System.currentTimeMillis())) {
            return;
        }

        int cantidad = ThreadLocalRandom.current().nextInt(XP_MIN, XP_MAX + 1);
        try {
            XpResultado resultado = xpService.ganarXp(usuarioId, cantidad);
            if (resultado.subioNivel()) {
                anunciarSubida(evento, resultado);
            }
        } catch (RuntimeException e) {
            // No romper el flujo de mensajes por un fallo de BD; se registra y se sigue.
            log.error("Error otorgando XP al usuario {}", usuarioId, e);
        }
    }

    /** Publica el embed de subida de nivel (dorado) en el canal del mensaje. */
    private void anunciarSubida(MessageReceivedEvent evento, XpResultado resultado) {
        // Anuncio público del servidor: idioma por defecto (la config por servidor llega después).
        MessageEmbed embed = EmbedFactory.base(
                        EmbedFactory.Tipo.LOGRO,
                        Messages.ES,
                        Messages.get(Messages.ES, "xp.subida.titulo"),
                        Messages.get(Messages.ES, "xp.subida.desc",
                                evento.getAuthor().getAsMention(), resultado.nivelNuevo()))
                .setThumbnail(evento.getAuthor().getEffectiveAvatarUrl())
                .build();
        evento.getChannel().sendMessageEmbeds(embed).queue();
    }
}
