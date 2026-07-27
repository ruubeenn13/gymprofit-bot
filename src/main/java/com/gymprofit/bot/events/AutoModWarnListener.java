package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.moderacion.AplicadorSanciones;
import com.gymprofit.bot.commands.moderacion.ModHelper;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.util.Cooldown;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.automod.AutoModExecutionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Puente entre el AutoMod <b>nativo</b> de Discord (reglas configuradas en el servidor: palabras
 * baneadas, spam, mención masiva…) y el aviso interno del bot: cada ejecución de una regla cuenta
 * como una amonestación de {@link AplicadorSanciones}, así que también entra en el escalado
 * (timeout/ban) y en el historial de {@code /modlogs}.
 *
 * <p>El evento de JDA ({@code AutoModExecutionEvent}) solo expone el id del usuario infractor, no
 * el {@link Member} ni el {@link User}: se resuelve el miembro desde la caché del guild (el bot
 * cachea todos los miembros, ver {@code DiscordBot}), y de ahí su {@code User}. Si el miembro no
 * está en caché (caso raro: salió del servidor justo tras el disparo) se descarta sin amonestar,
 * porque no hay forma síncrona de construir un {@code User} sin una llamada REST adicional.</p>
 *
 * <p>El personal ({@link ModHelper#esAltoCargo}) y los bots quedan exentos (igual que en
 * {@link AntiAbusoListener}). Un anti-ráfaga de 30&nbsp;s por usuario evita apilar avisos cuando
 * una misma ráfaga de mensajes dispara la regla varias veces seguidas.</p>
 *
 * <p>El evento no expone el nombre de la regla (solo su id, {@code getRuleIdLong()}); resolverlo
 * a nombre exigiría una llamada REST asíncrona a {@code guild.retrieveAutoModRules()}, que no
 * encaja en el flujo síncrono de {@link AplicadorSanciones#aplicar}. En su lugar se usa
 * {@code getTriggerType()} (p. ej. {@code KEYWORD}, {@code SPAM}, {@code MENTION_SPAM}), un dato
 * real y síncrono del evento que identifica igualmente qué tipo de regla saltó.</p>
 */
public final class AutoModWarnListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AutoModWarnListener.class);

    private final AplicadorSanciones aplicador;

    /** Anti-ráfaga: no vuelve a amonestar al mismo usuario antes de que pasen 30 s. */
    private final Cooldown antiRafaga = new Cooldown(Duration.ofSeconds(30));

    public AutoModWarnListener(AplicadorSanciones aplicador) {
        this.aplicador = aplicador;
    }

    @Override
    public void onAutoModExecution(AutoModExecutionEvent evento) {
        Guild guild = evento.getGuild();
        long usuarioId = evento.getUserIdLong();
        // MemberCachePolicy.ALL (DiscordBot) mantiene en caché a todos los miembros del guild: un
        // AutoMod nativo solo se dispara por mensajes de miembros reales, así que esto casi nunca es null.
        Member miembro = guild.getMemberById(usuarioId);
        if (miembro != null && (miembro.getUser().isBot() || ModHelper.esAltoCargo(miembro))) {
            return;
        }

        User usuario = miembro != null ? miembro.getUser() : guild.getJDA().getUserById(usuarioId);
        if (usuario == null) {
            log.debug("AutoMod: usuario {} no está en caché, no se puede amonestar.", usuarioId);
            return;
        }

        long ahora = System.currentTimeMillis();
        if (!antiRafaga.intentar(usuarioId, ahora)) {
            log.debug("AutoMod: {} sigue en la ventana anti-ráfaga, no se amonesta de nuevo.", usuarioId);
            return;
        }

        String nombreRegla = evento.getTriggerType().name();
        String motivo = Messages.get(Messages.ES, "moderacion.motivo.automod", nombreRegla);
        aplicador.aplicar(guild, usuario, miembro, guild.getJDA().getSelfUser().getIdLong(), motivo);
    }
}
