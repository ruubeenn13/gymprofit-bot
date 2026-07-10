package com.gymprofit.bot;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.commands.RouterComandos;
import com.gymprofit.bot.commands.admin.SetupComando;
import com.gymprofit.bot.commands.config.ConfigComando;
import com.gymprofit.bot.commands.gamificacion.NivelComando;
import com.gymprofit.bot.commands.gamificacion.TopComando;
import com.gymprofit.bot.commands.general.PingComando;
import com.gymprofit.bot.commands.moderacion.LimpiarComando;
import com.gymprofit.bot.config.BotConfig;
import com.gymprofit.bot.db.ConfigServidorRepositorio;
import com.gymprofit.bot.db.Database;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.events.BienvenidaListener;
import com.gymprofit.bot.events.PanelRolesListener;
import com.gymprofit.bot.events.XpMensajeListener;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.EstadisticasService;
import com.gymprofit.bot.services.LimpiezaService;
import com.gymprofit.bot.services.XpService;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
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
        JDA jda = iniciarDiscord(db);

        // Job de contadores en vivo (categoría SERVER STATS). No depende de BD; lee la caché de
        // miembros/presencias. El primer tick espera un poco a que se resuelva esa caché.
        EstadisticasService stats = (jda == null) ? null : new EstadisticasService(jda);
        if (stats != null) {
            stats.iniciar();
        }

        // Cierre ordenado ante SIGTERM (Render lo envía en cada deploy): stats → JDA → BD → health.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (stats != null) {
                stats.detener();
            }
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
                // El avatar del bot acompaña al footer de todos los embeds (marca visual).
                EmbedFactory.configurarIconoFooter(jda.getSelfUser().getEffectiveAvatarUrl());
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
     * Construye y conecta JDA con sus comandos y listeners. Devuelve {@code null} si no hay
     * {@code DISCORD_TOKEN} (arranque degradado solo con health server).
     *
     * <p>Los comandos y el listener de XP que dependen de la BD solo se registran si hay BD; sin
     * ella queda disponible {@code /ping}.</p>
     *
     * @param db la BD ya conectada, o {@code null} si se arrancó sin BD
     */
    private static JDA iniciarDiscord(Database db) {
        if (BotConfig.discordToken().isBlank()) {
            log.warn("DISCORD_TOKEN no presente: JDA no se conectará (solo health server).");
            return null;
        }

        List<Comando> comandos = new ArrayList<>();
        comandos.add(new PingComando());
        List<Object> listeners = new ArrayList<>();

        if (db != null) {
            UsuarioDiscordRepositorio usuarios = new UsuarioDiscordRepositorio(db.dataSource());
            XpService xpService = new XpService(usuarios);
            comandos.add(new NivelComando(usuarios));
            comandos.add(new TopComando(usuarios));
            listeners.add(new XpMensajeListener(xpService));

            ConfigServidorService configService =
                    new ConfigServidorService(new ConfigServidorRepositorio(db.dataSource()));
            comandos.add(new ConfigComando(configService));
            listeners.add(new BienvenidaListener(configService));
            listeners.add(new PanelRolesListener(configService));

            LimpiezaService limpieza = new LimpiezaService();
            comandos.add(new LimpiarComando(limpieza));
            comandos.add(new SetupComando(configService, limpieza));
        } else {
            log.warn("Sin BD: XP por mensaje y /nivel, /top deshabilitados; solo /ping disponible.");
        }

        listeners.add(new RouterComandos(comandos));
        return DiscordBot.start(BotConfig.discordToken(), listeners.toArray());
    }
}
