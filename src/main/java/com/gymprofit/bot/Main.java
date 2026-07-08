package com.gymprofit.bot;

import com.gymprofit.bot.config.BotConfig;
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
 * <p>En esta fase de andamiaje solo se arranca el health server y se registra la
 * configuración detectada; la conexión a Discord y a la BD se implementan en la Fase 1.</p>
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
            log.warn("DISCORD_TOKEN no presente: JDA no se conectará (esperado en andamiaje).");
        } else {
            log.info("DISCORD_TOKEN presente: la conexión JDA se implementa en la Fase 1.");
        }

        // Cierre ordenado del health server al recibir SIGTERM (Render lo envía en cada deploy).
        Runtime.getRuntime().addShutdownHook(new Thread(health::stop, "shutdown-health"));
    }
}
