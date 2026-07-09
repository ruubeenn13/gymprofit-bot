package com.gymprofit.bot;

import com.gymprofit.bot.config.BotConfig;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Punto de entrada de GymProBot.
 *
 * <p>Responsabilidades (por fases, ver {@code GYMPROBOT_SPEC.md} §4):</p>
 * <ol>
 *   <li>Levantar el {@link HealthServer} en {@code /health} (usado por Render y por
 *       {@code keep-alive.yml}). Se arranca <b>siempre</b>, aunque falte el token.</li>
 *   <li>(F1) Ejecutar las migraciones Flyway sobre la BD del bot.</li>
 *   <li>(F1) Construir y conectar JDA (slash commands, listeners, jobs).</li>
 * </ol>
 *
 * <p>Estado actual: health server + conexión JDA (sin comandos ni listeners todavía).
 * Si falta {@code DISCORD_TOKEN} se arranca solo el health server (útil para el andamiaje
 * y para el keep-alive de Render). La BD/Flyway se conecta en el siguiente paso de F1.</p>
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        log.info("Arrancando GymProBot {}...", BotConfig.version());

        HealthServer health = new HealthServer(BotConfig.port());
        health.start();
        log.info("Health server escuchando en http://0.0.0.0:{}/health", BotConfig.port());

        if (BotConfig.discordToken().isBlank()) {
            log.warn("DISCORD_TOKEN no presente: JDA no se conectará (solo health server).");
            // Cierre ordenado del health server al recibir SIGTERM (Render lo envía en cada deploy).
            Runtime.getRuntime().addShutdownHook(new Thread(health::stop, "shutdown-health"));
            return;
        }

        JDA jda = DiscordBot.start(BotConfig.discordToken());
        // Cierre ordenado de JDA + health al recibir SIGTERM (Render lo envía en cada deploy).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            jda.shutdown();
            health.stop();
        }, "shutdown"));

        try {
            jda.awaitReady();
            log.info("Conectado a Discord como {} (guilds: {})",
                    jda.getSelfUser().getName(), jda.getGuilds().size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrumpido esperando la conexión con Discord.", e);
        }
    }
}
