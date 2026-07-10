package com.gymprofit.bot;

import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Fábrica de la conexión a Discord (bootstrap de JDA, SPEC §4).
 *
 * <p>Aísla toda la configuración de {@link JDABuilder} en un único punto para poder
 * testear la selección de intents sin abrir una conexión real. En esta fase el bot se
 * conecta <b>sin comandos ni listeners</b>: solo valida token, intents y presencia; los
 * slash commands y eventos se registran en pasos posteriores de la Fase 1.</p>
 */
public final class DiscordBot {

    /**
     * Intents privilegiados que activamos <b>sobre</b> los intents por defecto de JDA
     * (los no privilegiados ya vienen habilitados por {@link JDABuilder#createDefault}).
     * Deben estar marcados en el Developer Portal → pestaña Bot:
     * <ul>
     *   <li>{@link GatewayIntent#GUILD_MEMBERS}: bienvenida y auto-roles (F1).</li>
     *   <li>{@link GatewayIntent#MESSAGE_CONTENT}: XP por mensaje y auto-mod (F1).</li>
     * </ul>
     *
     * <p>Los contadores de estadísticas (XP repartido, Nº1, boosts, gente en voz) usan datos de la
     * BD y de la caché estándar (estados de voz), así que <b>no</b> requieren intents privilegiados
     * adicionales.</p>
     */
    static final EnumSet<GatewayIntent> PRIVILEGED_INTENTS = EnumSet.of(
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.MESSAGE_CONTENT
    );

    private DiscordBot() {
    }

    /**
     * Construye y conecta la instancia de JDA. Devuelve inmediatamente mientras la
     * conexión se establece en segundo plano; usar {@link JDA#awaitReady()} si se
     * necesita esperar a que esté lista.
     *
     * @param token     token del bot (nunca se loggea)
     * @param listeners listeners a registrar antes de conectar (router de comandos, eventos…)
     * @return la instancia de JDA en proceso de conexión
     */
    public static JDA start(String token, Object... listeners) {
        // Timeout global de las RestActions: si una acción no puede completarse en 30 s (p. ej. un
        // rate limit 429 con retry-after enorme), falla en vez de bloquear el hilo durante horas.
        RestAction.setDefaultTimeout(30, TimeUnit.SECONDS);
        return JDABuilder.createDefault(token)
                // Intents privilegiados necesarios para F1; el resto (por defecto) ya están.
                .enableIntents(PRIVILEGED_INTENTS)
                // Cacheamos todos los miembros: la bienvenida y el XP los necesitan resueltos.
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.customStatus(Messages.get(Messages.ES, "bot.actividad")))
                // Se registran antes de build() para no perder eventos de arranque (GuildReady).
                .addEventListeners(listeners)
                .build();
    }
}
