package com.gymprofit.bot;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.commands.RouterComandos;
import com.gymprofit.bot.commands.admin.SetupComando;
import com.gymprofit.bot.commands.comunidad.EventoComando;
import com.gymprofit.bot.commands.comunidad.RetoComando;
import com.gymprofit.bot.commands.comunidad.SugerenciaComando;
import com.gymprofit.bot.commands.comunidad.SugerenciaResolverComando;
import com.gymprofit.bot.commands.config.ConfigComando;
import com.gymprofit.bot.commands.config.PanelComando;
import com.gymprofit.bot.commands.contenido.AnuncioComando;
import com.gymprofit.bot.commands.contenido.RedesComando;
import com.gymprofit.bot.commands.contenido.SorteoComando;
import com.gymprofit.bot.commands.economia.BalanceComando;
import com.gymprofit.bot.commands.economia.ComprarComando;
import com.gymprofit.bot.commands.economia.DailyComando;
import com.gymprofit.bot.commands.economia.DesequiparComando;
import com.gymprofit.bot.commands.economia.EquiparComando;
import com.gymprofit.bot.commands.economia.ElegirTrabajoComando;
import com.gymprofit.bot.commands.economia.EncantarComando;
import com.gymprofit.bot.commands.economia.EntrenarComando;
import com.gymprofit.bot.commands.economia.InventarioComando;
import com.gymprofit.bot.commands.economia.MejorarComando;
import com.gymprofit.bot.commands.economia.MejorasComando;
import com.gymprofit.bot.commands.economia.MonstruosComando;
import com.gymprofit.bot.commands.economia.MundosComando;
import com.gymprofit.bot.commands.economia.PelearComando;
import com.gymprofit.bot.commands.economia.PerfilComando;
import com.gymprofit.bot.commands.economia.TiendaComando;
import com.gymprofit.bot.commands.economia.TrabajosComando;
import com.gymprofit.bot.commands.economia.UsarComando;
import com.gymprofit.bot.commands.economia.WorkComando;
import com.gymprofit.bot.commands.gamificacion.NivelComando;
import com.gymprofit.bot.commands.gamificacion.TopComando;
import com.gymprofit.bot.commands.general.PingComando;
import com.gymprofit.bot.commands.moderacion.BanComando;
import com.gymprofit.bot.commands.moderacion.ClearwarnsComando;
import com.gymprofit.bot.commands.moderacion.KickComando;
import com.gymprofit.bot.commands.moderacion.LimpiarComando;
import com.gymprofit.bot.commands.moderacion.LockComando;
import com.gymprofit.bot.commands.moderacion.LockdownComando;
import com.gymprofit.bot.commands.moderacion.ModlogsComando;
import com.gymprofit.bot.commands.moderacion.MotivoComando;
import com.gymprofit.bot.commands.moderacion.MuteComando;
import com.gymprofit.bot.commands.moderacion.NickComando;
import com.gymprofit.bot.commands.moderacion.TimeoutComando;
import com.gymprofit.bot.commands.moderacion.SlowmodeComando;
import com.gymprofit.bot.commands.moderacion.UnbanComando;
import com.gymprofit.bot.commands.moderacion.UnlockComando;
import com.gymprofit.bot.commands.moderacion.UnlockdownComando;
import com.gymprofit.bot.commands.moderacion.UnmuteComando;
import com.gymprofit.bot.commands.moderacion.UntimeoutComando;
import com.gymprofit.bot.commands.moderacion.UnwarnComando;
import com.gymprofit.bot.commands.moderacion.WarnComando;
import com.gymprofit.bot.commands.moderacion.WarnsComando;
import com.gymprofit.bot.commands.privacidad.BorrarMisDatosComando;
import com.gymprofit.bot.commands.privacidad.MisDatosComando;
import com.gymprofit.bot.commands.privacidad.PrivacidadComando;
import com.gymprofit.bot.config.BotConfig;
import com.gymprofit.bot.db.ConfigServidorRepositorio;
import com.gymprofit.bot.db.Database;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.MejoraRepositorio;
import com.gymprofit.bot.db.MundoRepositorio;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.EventoServidorRepositorio;
import com.gymprofit.bot.db.SancionRepositorio;
import com.gymprofit.bot.db.SorteoRepositorio;
import com.gymprofit.bot.db.SugerenciaRepositorio;
import com.gymprofit.bot.db.TicketRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.db.WarnRepositorio;
import com.gymprofit.bot.jobs.SorteoJob;
import com.gymprofit.bot.services.CombateService;
import com.gymprofit.bot.services.EconomiaService;
import com.gymprofit.bot.services.EncantarService;
import com.gymprofit.bot.services.EventoService;
import com.gymprofit.bot.services.ItemService;
import com.gymprofit.bot.services.MejoraService;
import com.gymprofit.bot.services.BatallaService;
import com.gymprofit.bot.services.ModeracionService;
import com.gymprofit.bot.services.MundoService;
import com.gymprofit.bot.services.PrivacidadService;
import com.gymprofit.bot.services.SorteoService;
import com.gymprofit.bot.services.SugerenciaService;
import com.gymprofit.bot.services.TicketService;
import com.gymprofit.bot.services.TrabajoService;
import com.gymprofit.bot.util.Cifrador;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.events.BienvenidaListener;
import com.gymprofit.bot.events.BorrarDatosListener;
import com.gymprofit.bot.events.CombateListener;
import com.gymprofit.bot.events.ModlogsPaginadorListener;
import com.gymprofit.bot.events.PanelRolesListener;
import com.gymprofit.bot.events.TicketListener;
import com.gymprofit.bot.events.XpMensajeListener;
import com.gymprofit.bot.jobs.EnergiaJob;
import com.gymprofit.bot.jobs.RetencionJob;
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

        // Job de contadores en vivo (categoría SERVER STATS). XP repartido y Nº1 salen de la BD
        // (si la hay); boosts y gente en voz, de la caché estándar. El primer tick espera un poco a
        // que se resuelva la caché de miembros/estados de voz.
        UsuarioDiscordRepositorio usuariosStats =
                (db == null) ? null : new UsuarioDiscordRepositorio(db.dataSource());
        EventoServidorRepositorio eventosStats =
                (db == null) ? null : new EventoServidorRepositorio(db.dataSource());
        EstadisticasService stats =
                (jda == null) ? null : new EstadisticasService(jda, usuariosStats, eventosStats);
        if (stats != null) {
            stats.iniciar();
        }

        // Job que resuelve los sorteos vencidos (elige ganadores por reacción). Requiere BD + JDA.
        if (db != null && jda != null) {
            new SorteoJob(jda, new SorteoService(new SorteoRepositorio(db.dataSource()))).iniciar();
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
            comandos.add(new SetupComando(configService));

            EventoService eventoService =
                    new EventoService(new EventoServidorRepositorio(db.dataSource()));
            comandos.add(new RetoComando(eventoService));
            comandos.add(new EventoComando(eventoService));

            // Moderación: avisos con escalado + auditoría cifrada (BOT_CRYPTO_KEY).
            Cifrador cifrador = new Cifrador(BotConfig.cryptoKey());
            if (!cifrador.habilitado()) {
                log.warn("BOT_CRYPTO_KEY no configurada: los motivos de moderación no se guardarán.");
            }
            WarnRepositorio warnRepo = new WarnRepositorio(db.dataSource());
            SancionRepositorio sancionRepo = new SancionRepositorio(db.dataSource());
            ModeracionService moderacion =
                    new ModeracionService(warnRepo, sancionRepo, usuarios, cifrador);
            comandos.add(new WarnComando(moderacion, configService));
            comandos.add(new WarnsComando(moderacion));
            comandos.add(new UnwarnComando(moderacion));
            comandos.add(new ClearwarnsComando(moderacion, configService));
            comandos.add(new MuteComando(moderacion, configService));
            comandos.add(new UnmuteComando(moderacion, configService));
            comandos.add(new TimeoutComando(moderacion, configService));
            comandos.add(new UntimeoutComando(moderacion, configService));
            comandos.add(new KickComando(moderacion, configService));
            comandos.add(new BanComando(moderacion, configService));
            comandos.add(new UnbanComando(moderacion, configService));
            comandos.add(new NickComando(moderacion, configService));
            comandos.add(new ModlogsComando(moderacion));
            comandos.add(new MotivoComando(moderacion));
            listeners.add(new ModlogsPaginadorListener(moderacion));
            comandos.add(new LockComando(configService));
            comandos.add(new UnlockComando(configService));
            comandos.add(new LockdownComando(configService));
            comandos.add(new UnlockdownComando(configService));
            comandos.add(new SlowmodeComando(configService));

            // Privacidad (RGPD): acceso, portabilidad, olvido + job de retención.
            PrivacidadService privacidad =
                    new PrivacidadService(usuarios, warnRepo, sancionRepo, cifrador);
            comandos.add(new PrivacidadComando());
            comandos.add(new MisDatosComando(privacidad));
            comandos.add(new BorrarMisDatosComando());
            listeners.add(new BorrarDatosListener(privacidad));
            new RetencionJob(warnRepo, sancionRepo).iniciar();

            // Contenido (staff): anuncios, redes y sorteos (el job de sorteos arranca en main()).
            comandos.add(new AnuncioComando());
            comandos.add(new RedesComando());
            comandos.add(new SorteoComando(new SorteoService(new SorteoRepositorio(db.dataSource()))));
            comandos.add(new PanelComando());

            // Tickets: panel por botón + canal privado + transcript al cerrar.
            listeners.add(new TicketListener(
                    new TicketService(new TicketRepositorio(db.dataSource()), usuarios)));

            // Sugerencias: post en el foro con votación + resolución por staff.
            SugerenciaService sugerenciaService =
                    new SugerenciaService(new SugerenciaRepositorio(db.dataSource()), usuarios);
            comandos.add(new SugerenciaComando(sugerenciaService));
            comandos.add(new SugerenciaResolverComando(sugerenciaService));

            // Economía / RPG: monedero, daily, perfil, trabajos y energía.
            PersonajeRepositorio personajeRepo = new PersonajeRepositorio(db.dataSource());
            EconomiaRepositorio economiaRepo = new EconomiaRepositorio(db.dataSource());
            EconomiaService economiaService =
                    new EconomiaService(economiaRepo, personajeRepo, usuarios);
            comandos.add(new BalanceComando(economiaService));
            comandos.add(new DailyComando(economiaService));
            comandos.add(new PerfilComando(economiaService));

            TrabajoService trabajoService = new TrabajoService(personajeRepo, economiaRepo, usuarios);
            comandos.add(new TrabajosComando());
            comandos.add(new ElegirTrabajoComando(trabajoService));
            comandos.add(new WorkComando(trabajoService));
            comandos.add(new EntrenarComando(trabajoService));
            new EnergiaJob(personajeRepo).iniciar();

            // Tienda e inventario.
            InventarioRepositorio inventarioRepo = new InventarioRepositorio(db.dataSource());
            ItemService itemService =
                    new ItemService(economiaRepo, inventarioRepo, personajeRepo, usuarios);
            comandos.add(new TiendaComando());
            comandos.add(new ComprarComando(itemService));
            comandos.add(new InventarioComando(itemService));
            comandos.add(new UsarComando(itemService));

            // Combate (COMBAT-1): equipar arma/armadura y poder de combate.
            CombateService combateService =
                    new CombateService(personajeRepo, inventarioRepo, usuarios);
            comandos.add(new EquiparComando(combateService));
            comandos.add(new DesequiparComando(combateService));

            // Combate (COMBAT-2): mundos y bestiario (datos + navegación, sin pelea aún).
            MundoRepositorio mundoRepo = new MundoRepositorio(db.dataSource());
            MundoService mundoService = new MundoService(mundoRepo, usuarios);
            comandos.add(new MundosComando(mundoService));
            comandos.add(new MonstruosComando());

            // Combate (COMBAT-3): batalla por turnos con botones.
            BatallaService batallaService = new BatallaService(
                    personajeRepo, inventarioRepo, usuarios, economiaRepo, xpService, mundoRepo);
            comandos.add(new PelearComando(mundoService));
            listeners.add(new CombateListener(batallaService, inventarioRepo));

            // Combate (COMBAT-4c): encantar el arma (nivel + efectos).
            comandos.add(new EncantarComando(
                    new EncantarService(personajeRepo, economiaRepo, usuarios)));

            // Árbol de mejoras (sube atributos permanentemente).
            MejoraService mejoraService = new MejoraService(
                    new MejoraRepositorio(db.dataSource()), economiaRepo, personajeRepo, usuarios);
            comandos.add(new MejorasComando(mejoraService));
            comandos.add(new MejorarComando(mejoraService));
        } else {
            log.warn("Sin BD: XP por mensaje y /nivel, /top deshabilitados; solo /ping disponible.");
        }

        listeners.add(new RouterComandos(comandos));
        return DiscordBot.start(BotConfig.discordToken(), listeners.toArray());
    }
}
