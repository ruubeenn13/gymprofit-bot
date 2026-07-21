package com.gymprofit.bot;

import com.gymprofit.bot.api.ApiClient;
import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.commands.RouterComandos;
import com.gymprofit.bot.commands.admin.SetupComando;
import com.gymprofit.bot.commands.comunidad.EventoComando;
import com.gymprofit.bot.commands.comunidad.RetoComando;
import com.gymprofit.bot.commands.comunidad.SugerenciaComando;
import com.gymprofit.bot.commands.comunidad.SugerenciaResolverComando;
import com.gymprofit.bot.commands.config.ConfigComando;
import com.gymprofit.bot.commands.consultas.EjercicioDiaComando;
import com.gymprofit.bot.commands.consultas.EjerciciosComando;
import com.gymprofit.bot.commands.consultas.FraseComando;
import com.gymprofit.bot.commands.contenido.PublicarComando;
import com.gymprofit.bot.commands.economia.AbrirComando;
import com.gymprofit.bot.commands.economia.BancoComando;
import com.gymprofit.bot.commands.economia.BolsaComando;
import com.gymprofit.bot.commands.economia.CasinoComando;
import com.gymprofit.bot.commands.economia.CofresComando;
import com.gymprofit.bot.commands.economia.ComprarComando;
import com.gymprofit.bot.commands.economia.CrafteoComando;
import com.gymprofit.bot.commands.economia.DescansoComando;
import com.gymprofit.bot.commands.economia.GremioComando;
import com.gymprofit.bot.commands.economia.DailyComando;
import com.gymprofit.bot.commands.economia.DesequiparComando;
import com.gymprofit.bot.commands.economia.EquiparComando;
import com.gymprofit.bot.commands.economia.EncantarComando;
import com.gymprofit.bot.commands.economia.EntrenarComando;
import com.gymprofit.bot.commands.economia.EstudiarComando;
import com.gymprofit.bot.commands.economia.InventarioComando;
import com.gymprofit.bot.commands.economia.MejorarComando;
import com.gymprofit.bot.commands.economia.MejorasComando;
import com.gymprofit.bot.commands.economia.MazmorraComando;
import com.gymprofit.bot.commands.economia.MercadoComando;
import com.gymprofit.bot.commands.economia.MinarComando;
import com.gymprofit.bot.commands.economia.MisionesComando;
import com.gymprofit.bot.commands.economia.MonstruosComando;
import com.gymprofit.bot.commands.economia.MundosComando;
import com.gymprofit.bot.commands.economia.PelearComando;
import com.gymprofit.bot.commands.economia.RecetasComando;
import com.gymprofit.bot.commands.economia.RegalarComando;
import com.gymprofit.bot.commands.economia.RegalarItemComando;
import com.gymprofit.bot.commands.economia.RepararComando;
import com.gymprofit.bot.commands.economia.RobarComando;
import com.gymprofit.bot.commands.economia.PerfilComando;
import com.gymprofit.bot.commands.economia.RankComando;
import com.gymprofit.bot.commands.economia.TiendaComando;
import com.gymprofit.bot.commands.economia.TruequeComando;
import com.gymprofit.bot.commands.economia.TrabajoComando;
import com.gymprofit.bot.commands.gamificacion.NivelComando;
import com.gymprofit.bot.commands.gamificacion.TopComando;
import com.gymprofit.bot.commands.general.PingComando;
import com.gymprofit.bot.commands.moderacion.BanComando;
import com.gymprofit.bot.commands.moderacion.KickComando;
import com.gymprofit.bot.commands.moderacion.LimpiarComando;
import com.gymprofit.bot.commands.moderacion.CanalComando;
import com.gymprofit.bot.commands.moderacion.ModlogsComando;
import com.gymprofit.bot.commands.moderacion.MotivoComando;
import com.gymprofit.bot.commands.moderacion.SilenciarComando;
import com.gymprofit.bot.commands.moderacion.NickComando;
import com.gymprofit.bot.commands.moderacion.UnbanComando;
import com.gymprofit.bot.commands.moderacion.WarnComando;
import com.gymprofit.bot.commands.privacidad.PrivacidadComando;
import com.gymprofit.bot.config.BotConfig;
import com.gymprofit.bot.db.ConfigServidorRepositorio;
import com.gymprofit.bot.db.Database;
import com.gymprofit.bot.db.DescansoRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.BancoRepositorio;
import com.gymprofit.bot.db.BolsaRepositorio;
import com.gymprofit.bot.db.GremioRepositorio;
import com.gymprofit.bot.db.InsigniaRepositorio;
import com.gymprofit.bot.db.MejoraRepositorio;
import com.gymprofit.bot.db.MercadoRepositorio;
import com.gymprofit.bot.db.MineriaRepositorio;
import com.gymprofit.bot.db.MisionRepositorio;
import com.gymprofit.bot.db.MundoRepositorio;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.EventoServidorRepositorio;
import com.gymprofit.bot.db.EjercicioDiaRepositorio;
import com.gymprofit.bot.db.FraseRepositorio;
import com.gymprofit.bot.db.SancionRepositorio;
import com.gymprofit.bot.db.SorteoRepositorio;
import com.gymprofit.bot.db.SugerenciaRepositorio;
import com.gymprofit.bot.db.TicketRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.db.WarnRepositorio;
import com.gymprofit.bot.jobs.BolsaJob;
import com.gymprofit.bot.jobs.SorteoJob;
import com.gymprofit.bot.services.ApuestaService;
import com.gymprofit.bot.services.BancoService;
import com.gymprofit.bot.services.BolsaService;
import com.gymprofit.bot.services.CofreService;
import com.gymprofit.bot.services.DueloService;
import com.gymprofit.bot.services.GremioService;
import com.gymprofit.bot.services.CombateService;
import com.gymprofit.bot.services.CrafteoService;
import com.gymprofit.bot.services.DescansoService;
import com.gymprofit.bot.services.EconomiaService;
import com.gymprofit.bot.services.EjercicioDiaService;
import com.gymprofit.bot.services.EjercicioService;
import com.gymprofit.bot.services.EncantarService;
import com.gymprofit.bot.services.EventoService;
import com.gymprofit.bot.services.InsigniaService;
import com.gymprofit.bot.services.ItemService;
import com.gymprofit.bot.services.MejoraService;
import com.gymprofit.bot.services.BatallaService;
import com.gymprofit.bot.services.MercadoService;
import com.gymprofit.bot.services.TruequeRegistro;
import com.gymprofit.bot.services.TruequeService;
import com.gymprofit.bot.services.MineriaService;
import com.gymprofit.bot.services.MisionService;
import com.gymprofit.bot.services.ModeracionService;
import com.gymprofit.bot.services.MundoService;
import com.gymprofit.bot.services.RangoService;
import com.gymprofit.bot.services.RegaloService;
import com.gymprofit.bot.services.RoboService;
import com.gymprofit.bot.services.VentaService;
import com.gymprofit.bot.services.PrivacidadService;
import com.gymprofit.bot.services.SorteoService;
import com.gymprofit.bot.services.SugerenciaService;
import com.gymprofit.bot.services.TicketService;
import com.gymprofit.bot.services.TrabajoService;
import com.gymprofit.bot.util.Cifrador;
import com.gymprofit.bot.util.Cooldown;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.events.BienvenidaListener;
import com.gymprofit.bot.events.BorrarDatosListener;
import com.gymprofit.bot.events.CombateListener;
import com.gymprofit.bot.events.DescansoListener;
import com.gymprofit.bot.events.ReintentoRegistro;
import com.gymprofit.bot.events.DueloListener;
import com.gymprofit.bot.events.TruequeListener;
import com.gymprofit.bot.events.EjerciciosPaginadorListener;
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
        // La capa API se construye una sola vez aquí: la comparten los comandos de consulta y
        // (fase siguiente) el job del ejercicio del día, que necesita JDA ya conectado.
        CapaApi capaApi = iniciarCapaApi(db);
        JDA jda = iniciarDiscord(db, capaApi);

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
            // El cliente HTTP de la API tiene executor y pool propios: sin cerrarlos, sus hilos
            // mantendrían viva la JVM tras el SIGTERM.
            if (capaApi != null) {
                capaApi.cliente().cerrar();
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

    /** Piezas de la capa API construidas una sola vez y compartidas por comandos y job. */
    private record CapaApi(ApiClient cliente, EjercicioService ejercicios,
                           EjercicioDiaService eleccion) { }

    /**
     * Construye la capa de consultas a la API GymProFit (F1). Devuelve {@code null} si falta la
     * BD (la elección del día se persiste) o las credenciales de la cuenta de servicio: en ese
     * caso el bot arranca sin {@code /ejercicios} ni {@code /ejercicio-dia} (arranque degradado,
     * mismo patrón que BD/JDA).
     *
     * @param db la BD ya conectada, o {@code null} si se arrancó sin BD
     */
    private static CapaApi iniciarCapaApi(Database db) {
        if (db == null || BotConfig.apiUrl().isBlank() || BotConfig.botServiceUser().isBlank()
                || BotConfig.botServicePassword().isBlank()) {
            log.warn("Sin BD o sin GYMPROFIT_API_URL / BOT_SERVICE_USER / BOT_SERVICE_PASSWORD: "
                    + "/ejercicios y /ejercicio-dia deshabilitados.");
            return null;
        }
        ApiClient cliente = new ApiClient(BotConfig.apiUrl(), BotConfig.botServiceUser(),
                BotConfig.botServicePassword());
        EjercicioService ejercicios = new EjercicioService(cliente.ejercicios());
        EjercicioDiaService eleccion = new EjercicioDiaService(
                new EjercicioDiaRepositorio(db.dataSource()), ejercicios);
        return new CapaApi(cliente, ejercicios, eleccion);
    }

    /**
     * Construye y conecta JDA con sus comandos y listeners. Devuelve {@code null} si no hay
     * {@code DISCORD_TOKEN} (arranque degradado solo con health server).
     *
     * <p>Los comandos y el listener de XP que dependen de la BD solo se registran si hay BD; sin
     * ella queda disponible {@code /ping}.</p>
     *
     * @param db      la BD ya conectada, o {@code null} si se arrancó sin BD
     * @param capaApi la capa de consultas a la API, o {@code null} si no está configurada
     */
    private static JDA iniciarDiscord(Database db, CapaApi capaApi) {
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
            RangoService rangoService = new RangoService();
            comandos.add(new NivelComando(usuarios));
            comandos.add(new TopComando(usuarios));
            listeners.add(new XpMensajeListener(xpService, rangoService));

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
            // /warn agrupa poner/lista/quitar/limpiar; /silenciar, mute+timeout; /canal, los bloqueos.
            comandos.add(new WarnComando(moderacion, configService));
            comandos.add(new SilenciarComando(moderacion, configService));
            comandos.add(new KickComando(moderacion, configService));
            comandos.add(new BanComando(moderacion, configService));
            comandos.add(new UnbanComando(moderacion, configService));
            comandos.add(new NickComando(moderacion, configService));
            comandos.add(new ModlogsComando(moderacion));
            comandos.add(new MotivoComando(moderacion));
            listeners.add(new ModlogsPaginadorListener(moderacion));
            comandos.add(new CanalComando(configService));

            // Privacidad (RGPD): acceso, portabilidad, olvido + job de retención. El repo de
            // descanso se crea aquí (y se reutiliza abajo) porque el export lo incluye.
            DescansoRepositorio descansoRepo = new DescansoRepositorio(db.dataSource());
            PrivacidadService privacidad =
                    new PrivacidadService(usuarios, warnRepo, sancionRepo, descansoRepo, cifrador);
            comandos.add(new PrivacidadComando(privacidad));
            listeners.add(new BorrarDatosListener(privacidad));
            new RetencionJob(warnRepo, sancionRepo).iniciar();

            // Contenido (staff): /publicar agrupa anuncio, redes, panel y sorteo
            // (el job de sorteos arranca en main()).
            comandos.add(new PublicarComando(
                    new SorteoService(new SorteoRepositorio(db.dataSource()))));

            // Tickets: panel por botón + canal privado + transcript al cerrar.
            listeners.add(new TicketListener(
                    new TicketService(new TicketRepositorio(db.dataSource()), usuarios)));

            // Sugerencias: post en el foro con votación + resolución por staff.
            SugerenciaService sugerenciaService =
                    new SugerenciaService(new SugerenciaRepositorio(db.dataSource()), usuarios);
            comandos.add(new SugerenciaComando(sugerenciaService));
            comandos.add(new SugerenciaResolverComando(sugerenciaService));

            // Consultas (F1): el banco de frases solo necesita BD (los seeds de la V2).
            FraseRepositorio fraseRepo = new FraseRepositorio(db.dataSource());
            comandos.add(new FraseComando(fraseRepo,
                    new Cooldown(java.time.Duration.ofSeconds(30))));

            // Consultas a la API (F1): catálogo y ejercicio del día. Solo si la capa API se pudo
            // construir (URL + credenciales de la cuenta de servicio).
            if (capaApi != null) {
                comandos.add(new EjerciciosComando(capaApi.ejercicios(),
                        capaApi.cliente().executor()));
                comandos.add(new EjercicioDiaComando(capaApi.eleccion(), capaApi.ejercicios(),
                        fraseRepo, capaApi.cliente().executor()));
                listeners.add(new EjerciciosPaginadorListener(capaApi.ejercicios(),
                        capaApi.cliente().executor()));
            }

            // Economía / RPG: monedero, daily, perfil, trabajos y energía.
            PersonajeRepositorio personajeRepo = new PersonajeRepositorio(db.dataSource());
            EconomiaRepositorio economiaRepo = new EconomiaRepositorio(db.dataSource());
            // El inventario se crea aquí arriba (y no con la tienda) porque el descanso lo necesita:
            // la cama sale del inventario y el descanso va antes que trabajo, batalla y minería.
            InventarioRepositorio inventarioRepo = new InventarioRepositorio(db.dataSource());
            EconomiaService economiaService =
                    new EconomiaService(economiaRepo, personajeRepo, usuarios);
            comandos.add(new DailyComando(economiaService));
            comandos.add(new RankComando(economiaService, usuarios, rangoService));

            // Descanso: dormir es un estado; al despertar se gana energía según cama y tiempo.
            // Se construye antes que TrabajoService, BatallaService y MineriaService: los tres lo
            // reciben por constructor para bloquear al que lo intente estando dormido.
            DescansoService descansoService = new DescansoService(
                    descansoRepo, personajeRepo, inventarioRepo, economiaRepo, usuarios);
            // Registro compartido de acciones bloqueadas por el sueño: quien bloquea la guarda
            // (currar, minar, pelear, mazmorra) y el descanso la relanza al despertar.
            ReintentoRegistro reintentos = new ReintentoRegistro();
            comandos.add(new DescansoComando(descansoService, reintentos));
            listeners.add(new DescansoListener(descansoService, reintentos));

            // /trabajo agrupa lista, elegir y currar.
            TrabajoService trabajoService =
                    new TrabajoService(personajeRepo, economiaRepo, usuarios, descansoService);
            comandos.add(new TrabajoComando(trabajoService, reintentos));
            comandos.add(new EntrenarComando(trabajoService));
            comandos.add(new EstudiarComando(trabajoService));
            new EnergiaJob(personajeRepo).iniciar();

            // Tienda e inventario.
            ItemService itemService =
                    new ItemService(economiaRepo, inventarioRepo, personajeRepo, usuarios, descansoService);
            VentaService ventaService = new VentaService(inventarioRepo, economiaRepo, usuarios);
            comandos.add(new TiendaComando());
            comandos.add(new ComprarComando(itemService));
            // /inventario agrupa ver, usar y vender.
            comandos.add(new InventarioComando(itemService, ventaService));

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
            BatallaService batallaService = new BatallaService(personajeRepo, inventarioRepo,
                    usuarios, economiaRepo, xpService, mundoRepo, descansoService);
            // Misiones de caza (COMBAT-6a): se completan al vencer en combate.
            MisionService misionService = new MisionService(
                    new MisionRepositorio(db.dataSource()), economiaRepo, xpService, usuarios);
            comandos.add(new PelearComando(mundoService));
            comandos.add(new MazmorraComando(mundoService));
            comandos.add(new MisionesComando(misionService));
            listeners.add(new CombateListener(batallaService, inventarioRepo, misionService, reintentos));

            // Combate (COMBAT-4c): encantar el arma (nivel + efectos).
            comandos.add(new EncantarComando(
                    new EncantarService(personajeRepo, economiaRepo, usuarios)));

            // Minería (COMBAT-5): minar recursos, repararlos picos y venderlos.
            MineriaRepositorio mineriaRepo = new MineriaRepositorio(db.dataSource());
            MineriaService mineriaService = new MineriaService(mineriaRepo,
                    personajeRepo, inventarioRepo, economiaRepo, usuarios, descansoService);
            comandos.add(new MinarComando(mineriaService, reintentos));
            comandos.add(new RepararComando(mineriaService));

            // Herrería (COMBAT-6 crafting): fabricar equipo con minerales.
            CrafteoService crafteoService = new CrafteoService(inventarioRepo, usuarios);
            comandos.add(new CrafteoComando(crafteoService));
            comandos.add(new RecetasComando());

            // Cofres: comprar y abrir por loot al azar.
            CofreService cofreService = new CofreService(
                    inventarioRepo, economiaRepo, personajeRepo, usuarios);
            comandos.add(new AbrirComando(cofreService));
            comandos.add(new CofresComando());

            // Progresión (F-ECO-3b): insignias/logros derivados del estado.
            // /perfil (ver + balance + insignias) se registra aquí porque necesita InsigniaService,
            // que a su vez depende de los repos de minería y mundos creados más arriba.
            InsigniaService insigniaService = new InsigniaService(
                    new InsigniaRepositorio(db.dataSource()), usuarios, personajeRepo,
                    mineriaRepo, mundoRepo);
            comandos.add(new PerfilComando(economiaService, insigniaService));

            // Economía entre jugadores (F-ECO-4a): regalar coins e ítems.
            RegaloService regaloService = new RegaloService(economiaRepo, inventarioRepo, usuarios);
            comandos.add(new RegalarComando(regaloService));
            comandos.add(new RegalarItemComando(regaloService));
            comandos.add(new RobarComando(new RoboService(economiaRepo, usuarios),
                    new Cooldown(java.time.Duration.ofMinutes(30))));

            // Mercado entre jugadores (F-ECO-4b): publicar, comprar, retirar.
            MercadoService mercadoService = new MercadoService(
                    new MercadoRepositorio(db.dataSource()), inventarioRepo, economiaRepo, usuarios);
            comandos.add(new MercadoComando(mercadoService));

            // Banco (F-ECO-4c): ahorro con interés y préstamos.
            BancoService bancoService = new BancoService(
                    new BancoRepositorio(db.dataSource()), economiaRepo, usuarios);
            comandos.add(new BancoComando(bancoService));

            // Trueque (F-ECO-4d): intercambio con confirmación por botones.
            TruequeRegistro truequeRegistro = new TruequeRegistro();
            comandos.add(new TruequeComando(truequeRegistro));
            listeners.add(new TruequeListener(
                    new TruequeService(economiaRepo, inventarioRepo, usuarios), truequeRegistro));

            // Gremios (F-ECO-5a): grupos con canal privado.
            GremioService gremioService = new GremioService(
                    new GremioRepositorio(db.dataSource()), economiaRepo, usuarios);
            comandos.add(new GremioComando(gremioService));

            // Casino (F-ECO-6): juegos de azar de ficción y duelos.
            ApuestaService apuestaService = new ApuestaService(economiaRepo, usuarios);
            Cooldown apuestasCooldown = new Cooldown(java.time.Duration.ofSeconds(5));
            DueloService dueloService = new DueloService(economiaRepo, usuarios);
            comandos.add(new CasinoComando(apuestaService, dueloService, apuestasCooldown));
            listeners.add(new DueloListener(dueloService));

            // Bolsa ficticia (extra): acciones con precio dinámico.
            BolsaService bolsaService = new BolsaService(
                    new BolsaRepositorio(db.dataSource()), economiaRepo, usuarios);
            comandos.add(new BolsaComando(bolsaService));
            new BolsaJob(bolsaService).iniciar();

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
