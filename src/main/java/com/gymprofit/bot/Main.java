package com.gymprofit.bot;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.commands.RouterComandos;
import com.gymprofit.bot.commands.general.PingComando;
import com.gymprofit.bot.config.BotConfig;
import com.gymprofit.bot.db.Database;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Punto de entrada de GymProBot.
 *
 * <p>Orden de arranque (ver {@code GYMPROBOT_SPEC.md} §4):</p>
 * <ol>
 *   <li>Levantar el {@link HealthServer} en {@code /health} (usado por Render y por
 *       {@code keep-alive.yml}). Se arranca <b>siempre</b>.</li>
 *   <li>Conectar la BD del bot y aplicar las migraciones Flyway ({@link Database}).</li>
 *   <li>Construir y conectar JDA registrando el router de slash commands (los listeners de
 *       eventos y los jobs programados llegan después).</li>
 * </ol>
 *
 * <p>Arranque degradado: si falta {@code DB_URL} se omiten BD/Flyway y si falta

 * {@code DISCORD_TOKEN} se omite JDA. Con solo {@code PORT} arranca el health server aislado
 * (útil para andamiaje y para el keep-alive de Render).</p>
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

        Database db = iniciarBaseDeDatos();
        JDA jda = iniciarDiscord();

        // Cierre ordenado ante SIGTERM (Render lo envía en cada deploy): JDA → BD → health.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (jda != null) {
                jda.shutdown();
            }
            if (db != null) {
                db.close();
            }
            health.stop();
        }, "shutdown"));

        if (jda != null) {
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

    /**
     * Conecta la BD del bot y aplica las migraciones Flyway. Devuelve {@code null} si no hay
     * {@code DB_URL} configurada (arranque degradado sin BD).
     */
    private static Database iniciarBaseDeDatos() {
        if (BotConfig.dbUrl().isBlank()) {
            log.warn("DB_URL no presente: se omiten la BD y las migraciones Flyway.");
            return null;
        }
        Database db = new Database(BotConfig.dbUrl(), BotConfig.dbUser(), BotConfig.dbPassword());
        db.migrar();
        return db;
    }

    /**
     * Construye y conecta JDA. Devuelve {@code null} si no hay {@code DISCORD_TOKEN}
     * (arranque degradado solo con health server).
     */
    private static JDA iniciarDiscord() {
        if (BotConfig.discordToken().isBlank()) {
            log.warn("DISCORD_TOKEN no presente: JDA no se conectará (solo health server).");
            return null;
        }
        // Router con los comandos de la Fase 1 (de momento /ping); se registran al conectar.
        List<Comando> comandos = List.of(new PingComando());
        return DiscordBot.start(BotConfig.discordToken(), new RouterComandos(comandos));
    }
}
