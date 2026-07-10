package com.gymprofit.bot.commands.admin;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.LimpiezaService;
import com.gymprofit.bot.services.SetupServidorPlan;
import com.gymprofit.bot.services.SetupServidorPlan.CanalPlan;
import com.gymprofit.bot.services.SetupServidorPlan.CategoriaPlan;
import com.gymprofit.bot.services.SetupServidorPlan.RolPlan;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildWelcomeScreen;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.automod.AutoModResponse;
import net.dv8tion.jda.api.entities.automod.AutoModRule;
import net.dv8tion.jda.api.entities.automod.AutoModTriggerType;
import net.dv8tion.jda.api.entities.automod.build.AutoModRuleData;
import net.dv8tion.jda.api.entities.automod.build.TriggerConfig;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.MediaChannel;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IPostContainer;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagData;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.restaction.ForumPostAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /setup}: monta la estructura del servidor (roles, categorías, canales, permisos y
 * mensajes fijados según {@link SetupServidorPlan}), purga los mensajes recientes de los canales
 * existentes y autorrellena {@code config_servidor}. Idempotente (reutiliza por nombre). Solo
 * admin.
 *
 * <p>El trabajo pesado corre en un hilo aparte ({@link CompletableFuture#runAsync}) para <b>no
 * bloquear el hilo del gateway</b> con las llamadas {@code complete()}. Comprueba los permisos
 * necesarios <b>antes</b> de tocar nada: si faltan, avisa y no deja el servidor a medias.</p>
 */
public final class SetupComando implements Comando {

    private static final Logger log = LoggerFactory.getLogger(SetupComando.class);
    private static final String NOMBRE = "setup";
    private static final String ROL_STAFF = "🧹 Staff";
    private static final String ROL_SILENCIADO = "🔇 Silenciado";
    private static final String CANAL_EMPIEZA = "🚀・empieza-aquí";
    private static final String CANAL_FAQ = "❓・faq";
    private static final String CANAL_SUGERENCIAS = "💡・sugerencias";

    /** Reacción por defecto de cada foro, temática según su contenido (fallback 👍). */
    private static final Map<String, String> REACCION_FORO = Map.of(
            CANAL_FAQ, "💡",
            "📚・rutinas", "💪",
            "🍎・nutrición", "🍎",
            "❓・dudas", "🙋",
            CANAL_SUGERENCIAS, "👍",
            "📥・reportes", "🚨",
            "📈・progresos", "🔥",
            "📸・fotos", "❤️");

    /**
     * Publicaciones iniciales por foro (nombre de canal → lista de {título, cuerpo, etiqueta}). La
     * etiqueta vacía = sin etiqueta. En español. Sirven de guía y de "primera publicación" para que
     * cada foro no arranque vacío.
     */
    private static final Map<String, List<String[]>> POSTS_FORO = Map.of(
            CANAL_FAQ, List.of(
                    new String[]{"¿Cómo gano XP?",
                            "Participando: cada mensaje te da XP y subes de nivel. Los detalles, en 🗺️・cómo-funciona.",
                            "XP y niveles"},
                    new String[]{"¿Cómo veo mi nivel y el ranking?",
                            "Usa **/nivel** para tu progreso y **/top** para la clasificación del servidor.",
                            "XP y niveles"},
                    new String[]{"¿Cómo consigo mis roles?",
                            "En 🎭・roles elige tu objetivo y tus notificaciones con los menús desplegables.",
                            "General"},
                    new String[]{"¿Qué es la economía?",
                            "Monedas, tienda y recompensas. Está en camino: lo verás en 🪙・economía.",
                            "Economía"},
                    new String[]{"¿Cómo vinculo la app GymProFit?",
                            "La vinculación con la app llegará pronto. Lo anunciaremos en 📣・anuncios.",
                            "App"},
                    new String[]{"¿Cómo pido ayuda al equipo?",
                            "En 🎫・soporte pulsa el botón para abrir un ticket privado con el staff.",
                            "General"},
                    new String[]{"¿Dónde comparto mis progresos?",
                            "Fotos antes/después en 📈・progresos, y tus entrenos en 📚・rutinas.",
                            "General"}),
            "📚・rutinas", List.<String[]>of(new String[]{"📌 Cómo compartir tu rutina", """
                    ¡Bienvenido a **rutinas**! 💪

                    Aquí compartimos entrenos. Para publicar la tuya:
                    • Abre un **post** con un título claro (ej: «Torso-Pierna 4 días»).
                    • Pon el objetivo, los días y los ejercicios con series × reps.
                    • Añade la **etiqueta** que corresponda (Push, Pull, Pierna, Full-body, Cardio, Movilidad).

                    Ejemplo — **Full-body 3 días**:
                    Sentadilla 3×8 · Press banca 3×8 · Remo 3×10 · Press militar 3×10 · Peso muerto rumano 3×10

                    ¡Comparte la tuya! 🔥""", "Full-body"}),
            "🍎・nutrición", List.<String[]>of(new String[]{"📌 Recetas, planes y dudas", """
                    Bienvenido a **nutrición** 🍎

                    Comparte recetas y planes, y resuelve dudas de alimentación. Al publicar:
                    • Título claro (ej: «Tortitas de avena proteicas»).
                    • Ingredientes, pasos y, si quieres, macros.
                    • Etiqueta: **Receta**, **Plan**, **Duda** o **Suplementación**.

                    Ejemplo — **Tortitas de avena (alto en proteína)**:
                    40 g de avena · 1 plátano · 2 huevos · 1 scoop de proteína. Tritura y a la sartén. 💪""", "Receta"}),
            "❓・dudas", List.<String[]>of(new String[]{"📌 Cómo preguntar (y resolver)", """
                    Bienvenido a **dudas** ❓

                    ¿Atascado con un ejercicio, con material o con una molestia? Pregunta aquí:
                    • Título con tu duda concreta.
                    • Explica qué te pasa y qué has probado.
                    • Etiqueta: **Técnica**, **Material** o **Lesión**.
                    • Cuando te la resuelvan, marca la etiqueta **Resuelto** ✅.

                    Nota: ante un dolor real, consulta a un profesional. 🩺""", "Técnica"}),
            CANAL_SUGERENCIAS, List.<String[]>of(new String[]{"📌 Cómo proponer mejoras", """
                    Bienvenido a **sugerencias** 💡

                    ¿Ideas para mejorar el servidor o la comunidad? Cuéntanoslas:
                    • Un **post** por idea, con un título claro.
                    • Describe la mejora y por qué aporta. Puedes adjuntar imagen.
                    • Vota las que te gusten con 👍.

                    El equipo revisará cada una y le pondrá su estado: **En estudio**, **Aprobada**, **Rechazada** o **Implementada**. ¡Gracias por construir esto con nosotros! 🚀""", ""}),
            "📥・reportes", List.<String[]>of(new String[]{"📌 Plantilla de reporte", """
                    Canal interno de **reportes** del staff.

                    Un **post** por caso, con esta plantilla:
                    • **Usuario:** @mención o ID
                    • **Motivo:** qué ha pasado
                    • **Pruebas:** capturas o enlaces a mensajes
                    • **Canal/hora:** dónde y cuándo

                    Estados (etiquetas): **Pendiente** → **En curso** → **Resuelto** / **Descartado**.""", "Pendiente"}),
            "📈・progresos", List.<String[]>of(new String[]{"📸 Comparte tu progreso", """
                    ¡Bienvenido a **progresos**! 📈

                    Sube tus avances y marcas: fotos antes/después, PRs, medidas… Una **publicación por avance**, con su foto. Apóyate y deja que te apoyen: aquí no se juzga, se motiva. 💪🔥""", ""}),
            "📸・fotos", List.<String[]>of(new String[]{"📸 Sube tus fotos", """
                    Bienvenido a **fotos** 📸

                    Comparte fotos de tus entrenos, comidas, material o del gym. Una foto por publicación. ¡A darle vida a la galería! 📷""", ""}));
    /** Canal (solo staff) al que AutoMod manda las alertas de contenido bloqueado. */
    private static final String CANAL_MODERACION = "📋・moderación";
    /** Categoría privada de staff (para recolocar dentro las anclas de comunidad). */
    private static final String CAT_STAFF = "▬▬ 🔒 STAFF ▬▬";
    /**
     * Canales-ancla permanentes y ocultos que sostienen los ajustes de comunidad (reglas y
     * updates/seguridad). Al no ser canales de contenido, Discord permite borrar y recrear el resto
     * libremente. Solo los ve el bot y el Fundador (Admin).
     */
    private static final String ANCLA_REGLAS = "📜・reglas-comunidad";
    private static final String ANCLA_AVISOS = "🛡️・avisos-comunidad";
    /** Categoría de contadores en vivo: se fuerza arriba del todo y sus canales de voz se bloquean. */
    private static final String CAT_STATS = "▬▬ 📊 SERVER STATS ▬▬";

    /** Nombres de las reglas de AutoMod que crea {@code /setup} (clave de idempotencia). */
    private static final String AUTOMOD_MENCIONES = "Anti-menciones masivas";
    private static final String AUTOMOD_SPAM = "Anti-spam";
    private static final String AUTOMOD_LENGUAJE = "Lenguaje inapropiado";
    /** Máximo de menciones únicas por mensaje antes de bloquear (raid/ping masivo). */
    private static final int AUTOMOD_LIMITE_MENCIONES = 8;

    /** Permisos que niega el rol Silenciado en todo el servidor. */
    private static final long DENY_SILENCIADO = Permission.getRaw(
            Permission.MESSAGE_SEND, Permission.MESSAGE_ADD_REACTION, Permission.VOICE_SPEAK);

    /**
     * Permisos que {@code /setup} asigna a los roles de gestión (el resto de roles se crean sin
     * permisos). Requiere que el bot tenga Administrador para poder otorgarlos.
     */
    private static final Map<String, Long> PERMISOS_ROL = Map.of(
            "👑 Fundador", Permission.ADMINISTRATOR.getRawValue(),
            "🛡️ Admin", Permission.ADMINISTRATOR.getRawValue(),
            "🧹 Staff", Permission.getRaw(
                    Permission.MESSAGE_MANAGE, Permission.MODERATE_MEMBERS,
                    Permission.KICK_MEMBERS, Permission.BAN_MEMBERS,
                    Permission.MANAGE_THREADS, Permission.NICKNAME_MANAGE,
                    Permission.VIEW_AUDIT_LOGS, Permission.MESSAGE_MENTION_EVERYONE));

    private final ConfigServidorService config;
    private final LimpiezaService limpieza;

    public SetupComando(ConfigServidorService config, LimpiezaService limpieza) {
        this.config = config;
        this.limpieza = limpieza;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData desdeCero = new OptionData(OptionType.BOOLEAN, "desde_cero",
                Messages.get(Messages.ES, "comando.setup.opcion.desde_cero"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.setup.opcion.desde_cero"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.setup.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.setup.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.setup.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addOptions(desdeCero);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Guild guild = evento.getGuild();
        boolean desdeCero = evento.getOption("desde_cero") != null
                && evento.getOption("desde_cero").getAsBoolean();

        evento.deferReply(true).queue();

        // Chequeo de permisos ANTES de tocar nada: si faltan, se avisa y no se rompe el servidor.
        Member yo = guild.getSelfMember();
        if (!yo.hasPermission(Permission.MANAGE_CHANNEL) || !yo.hasPermission(Permission.MANAGE_ROLES)) {
            evento.getHook().sendMessage(Messages.get(locale, "setup.faltan_permisos")).queue();
            return;
        }

        // Trabajo pesado en un HILO PROPIO (no en el commonPool, que JDA usa para sus callbacks:
        // bloquearlo con complete() causaba thread starvation y desconexiones).
        Thread hilo = new Thread(() -> {
            try {
                // Con Comunidad activada, Discord no deja borrar los canales de reglas/updates/
                // seguridad (error 50074). Solución: dos canales-ancla permanentes y ocultos
                // (reglas-comunidad y avisos-comunidad) sostienen esos ajustes. Se aseguran y anclan
                // en CADA /setup (para liberar los canales viejos y quedar como canales de comunidad)
                // y tras montar se recolocan en STAFF ocultos. Nunca se borran.
                boolean comunidad = guild.getFeatures().contains("COMMUNITY");
                Set<Long> conservar = new HashSet<>();
                if (comunidad) {
                    TextChannel anclaReglas = reusarOCrearAncla(guild, ANCLA_REGLAS);
                    TextChannel anclaAvisos = reusarOCrearAncla(guild, ANCLA_AVISOS);
                    anclarComunidad(guild, anclaReglas, anclaAvisos);
                    conservar.add(anclaReglas.getIdLong());
                    conservar.add(anclaAvisos.getIdLong());
                }

                List<GuildChannel> protegidos = new ArrayList<>();
                int limpiados = desdeCero
                        ? vaciarServidor(guild, protegidos, conservar)
                        : purgarCanalesExistentes(guild);
                Map<String, Role> roles = crearRoles(guild);
                int canales = crearCategoriasYCanales(guild, roles, locale);
                int reglasAutoMod = crearReglasAutoMod(guild);

                // Recoloca las anclas en STAFF y las oculta (solo bot + Fundador), en cada /setup.
                if (comunidad) {
                    colocarAnclas(guild, roles);
                }
                // Reintento por si quedó algún protegido (no debería, ya se anclaron antes de vaciar).
                for (GuildChannel viejo : protegidos) {
                    try {
                        viejo.delete().complete();
                        limpiados++;
                    } catch (RuntimeException e) {
                        log.warn("Sigue sin poder borrarse el canal {}", viejo.getName());
                    }
                }

                // Pantalla de bienvenida (Welcome Screen): lo único de la incorporación que expone
                // la API. El resto del onboarding (canales por defecto, preguntas) es manual.
                configurarBienvenida(guild);
                // Fija el canal AFK (Discord mueve ahí a los inactivos de voz).
                configurarAfk(guild);

                var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                        Messages.get(locale, "setup.titulo"),
                        Messages.get(locale, "setup.resumen",
                                SetupServidorPlan.ROLES.size(), canales, limpiados, reglasAutoMod)).build();
                // Si la interacción ya expiró (setup largo), el resumen falla sin pasa nada grave.
                evento.getHook().sendMessageEmbeds(embed).queue(null,
                        err -> log.info("Setup terminado, pero no se pudo enviar el resumen: {}",
                                err.toString()));
            } catch (RuntimeException e) {
                log.error("Error en /setup en el servidor {}", guild.getId(), e);
                evento.getHook().sendMessage(Messages.get(locale, "setup.error"))
                        .queue(null, ignorado -> { });
            }
        }, "gymprobot-setup");
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Vacía el servidor: borra todos los canales (los de {@code conservar} —anclas de comunidad—
     * nunca). Devuelve cuántos canales se borraron. Irreversible.
     *
     * <p><b>No borra roles a propósito.</b> Discord limita la <i>creación</i> de roles a un cupo
     * diario por servidor; borrar y recrear los 25 roles en cada {@code desde_cero} agotaba ese cupo
     * y bloqueaba la creación durante ~2 días (429 con retry-after enorme). Los roles se
     * <b>reutilizan</b> por nombre en {@link #crearRoles(Guild)}: se crean solo los que falten.</p>
     *
     * <p>Los canales que Discord rechace borrar se acumulan en {@code protegidosOut} para
     * reintentarlos al final (tras reapuntar la comunidad a las anclas).</p>
     */
    private int vaciarServidor(Guild guild, List<GuildChannel> protegidosOut, Set<Long> conservar) {
        int borrados = 0;
        for (GuildChannel canal : List.copyOf(guild.getChannels())) {
            // Las anclas de comunidad son permanentes: no se borran nunca.
            if (conservar.contains(canal.getIdLong())) {
                continue;
            }
            try {
                canal.delete().complete();
                borrados++;
            } catch (RuntimeException e) {
                // Probablemente un canal protegido por la comunidad; se reintenta más tarde.
                protegidosOut.add(canal);
                log.warn("No se pudo borrar el canal {} (se reintentará al final)", canal.getName());
            }
        }
        return borrados;
    }

    /** Purga mensajes recientes de todos los canales de texto existentes; devuelve cuántos. */
    private int purgarCanalesExistentes(Guild guild) {
        int total = 0;
        for (TextChannel canal : guild.getTextChannels()) {
            try {
                total += limpieza.purgarReciente(canal, LimpiezaService.MAX).join();
            } catch (RuntimeException e) {
                log.warn("No se pudo limpiar el canal {}", canal.getId(), e);
            }
        }
        return total;
    }

    /** Crea (o reutiliza) los roles con sus permisos; devuelve un mapa nombre → rol. */
    private Map<String, Role> crearRoles(Guild guild) {
        Map<String, Role> creados = new HashMap<>();
        for (RolPlan plan : SetupServidorPlan.ROLES) {
            try {
                long permisos = PERMISOS_ROL.getOrDefault(plan.nombre(), 0L);
                Role rol = guild.getRolesByName(plan.nombre(), false).stream().findFirst().orElse(null);
                if (rol == null) {
                    var accion = guild.createRole().setName(plan.nombre());
                    if (plan.colorRgb() != 0) {
                        accion = accion.setColor(new Color(plan.colorRgb()));
                    }
                    if (permisos != 0) {
                        accion = accion.setPermissions(permisos);
                    }
                    rol = accion.complete();
                } else if (permisos != 0) {
                    rol.getManager().setPermissions(permisos).complete();
                }
                creados.put(plan.nombre(), rol);
                if (plan.objetivo() != null) {
                    config.fijarRol(guild.getIdLong(), plan.objetivo(), rol.getIdLong());
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo crear/ajustar el rol {}", plan.nombre(), e);
            }
        }
        return creados;
    }

    /** Crea (o reutiliza) categorías y canales con permisos y pins; devuelve cuántos canales. */
    private int crearCategoriasYCanales(Guild guild, Map<String, Role> roles, Locale locale) {
        int canales = 0;
        Role everyone = guild.getPublicRole();
        Role staff = roles.get(ROL_STAFF);
        Role silenciado = roles.get(ROL_SILENCIADO);
        Map<String, Long> idsPorNombre = new HashMap<>();
        boolean empiezaNueva = false;

        for (CategoriaPlan catPlan : SetupServidorPlan.CATEGORIAS) {
            try {
                Category categoria = guild.getCategoriesByName(catPlan.nombre(), false)
                        .stream().findFirst().orElse(null);
                if (categoria == null) {
                    var accion = guild.createCategory(catPlan.nombre());
                    if (catPlan.oculta()) {
                        accion = accion.addRolePermissionOverride(everyone.getIdLong(),
                                0L, Permission.VIEW_CHANNEL.getRawValue());
                        if (catPlan.soloStaff() && staff != null) {
                            accion = accion.addRolePermissionOverride(staff.getIdLong(),
                                    Permission.VIEW_CHANNEL.getRawValue(), 0L);
                        }
                        // El bot debe ver la categoría oculta para crear/gestionar dentro.
                        accion = accion.addMemberPermissionOverride(guild.getSelfMember().getIdLong(),
                                Permission.VIEW_CHANNEL.getRawValue(), 0L);
                    }
                    if (silenciado != null) {
                        accion = accion.addRolePermissionOverride(silenciado.getIdLong(), 0L, DENY_SILENCIADO);
                    }
                    // STATS: todos VEN los contadores pero nadie se conecta (canales de solo lectura).
                    if (CAT_STATS.equals(catPlan.nombre())) {
                        accion = accion.addRolePermissionOverride(everyone.getIdLong(),
                                0L, Permission.VOICE_CONNECT.getRawValue());
                    }
                    categoria = accion.complete();
                }

                // La categoría de stats va arriba del todo (nuevo o reutilizado).
                if (CAT_STATS.equals(catPlan.nombre())) {
                    try {
                        categoria.getManager().setPosition(0).complete();
                    } catch (RuntimeException e) {
                        log.warn("No se pudo colocar la categoría de stats arriba", e);
                    }
                }

                for (CanalPlan chPlan : catPlan.canales()) {
                    canales++;
                    try {
                        GuildChannel existente = guild.getChannels().stream()
                                .filter(c -> mismoCanal(c, chPlan))
                                .findFirst().orElse(null);
                        GuildChannel canal;
                        if (existente != null) {
                            aplicarConfig(guild, existente, chPlan);
                            aplicarTopic(existente, chPlan);
                            canal = existente;
                        } else {
                            canal = crearCanal(guild, categoria, chPlan, everyone, staff, locale);
                            if (CANAL_EMPIEZA.equals(chPlan.nombre())) {
                                empiezaNueva = true;
                            }
                        }
                        idsPorNombre.put(chPlan.nombre(), canal.getIdLong());
                    } catch (RuntimeException e) {
                        log.warn("No se pudo crear el canal {}", chPlan.nombre(), e);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo crear la categoría {}", catPlan.nombre(), e);
            }
        }

        // Panel de accesos rápidos (botones que llevan a los canales clave) en 🚀 empieza-aquí.
        if (empiezaNueva) {
            publicarNavegacion(guild, idsPorNombre, locale);
        }
        return canales;
    }

    /** Crea un canal nuevo bajo su categoría, sincroniza permisos, fija intro y config. */
    private GuildChannel crearCanal(Guild guild, Category categoria, CanalPlan chPlan, Role everyone,
                                    Role staff, Locale locale) {
        GuildChannel creado;
        if (chPlan.tipo() == SetupServidorPlan.TipoCanalDiscord.VOZ) {
            var accionVoz = categoria.createVoiceChannel(chPlan.nombre());
            if (chPlan.limiteVoz() > 0) {
                accionVoz = accionVoz.setUserlimit(chPlan.limiteVoz());
            }
            VoiceChannel vc = accionVoz.complete();
            vc.getManager().sync().complete();
            creado = vc;
        } else if (chPlan.tipo() == SetupServidorPlan.TipoCanalDiscord.ESCENARIO) {
            StageChannel sc = categoria.createStageChannel(chPlan.nombre()).complete();
            sc.getManager().sync().complete();
            creado = sc;
        } else if (chPlan.tipo() == SetupServidorPlan.TipoCanalDiscord.FORO) {
            var accion = categoria.createForumChannel(chPlan.nombre());
            if (chPlan.topic() != null) {
                accion = accion.setTopic(chPlan.topic());
            }
            if (!chPlan.etiquetas().isEmpty()) {
                accion = accion.setAvailableTags(
                        chPlan.etiquetas().stream().map(ForumTagData::new).toList());
            }
            // Todos los foros llevan reacción por defecto (temática) y su primera publicación.
            accion = accion.setDefaultReaction(reaccionDe(chPlan.nombre()));
            ForumChannel fc = accion.complete();
            fc.getManager().sync().complete();
            publicarPostsIniciales(fc, chPlan.nombre());
            creado = fc;
        } else if (chPlan.tipo() == SetupServidorPlan.TipoCanalDiscord.MEDIA) {
            creado = crearMediaOForo(categoria, chPlan);
        } else if (chPlan.tipo() == SetupServidorPlan.TipoCanalDiscord.ANUNCIOS) {
            var accion = categoria.createNewsChannel(chPlan.nombre());
            if (chPlan.topic() != null) {
                accion = accion.setTopic(chPlan.topic());
            }
            NewsChannel nc = accion.complete();
            nc.getManager().sync().complete();
            // El staff anuncia; @everyone lee pero no escribe.
            if (chPlan.soloLectura()) {
                aplicarSoloLectura(nc, everyone, staff);
            }
            fijarIntro(nc, chPlan, locale);
            creado = nc;
        } else {
            var accion = categoria.createTextChannel(chPlan.nombre());
            if (chPlan.slowmodeSegundos() > 0) {
                accion = accion.setSlowmode(chPlan.slowmodeSegundos());
            }
            if (chPlan.topic() != null) {
                accion = accion.setTopic(chPlan.topic());
            }
            TextChannel tc = accion.complete();
            // Hereda los permisos de la categoría (ocultación, staff, silenciado).
            tc.getManager().sync().complete();
            if (chPlan.soloLectura()) {
                aplicarSoloLectura(tc, everyone, staff);
            }
            if ("panel.roles".equals(chPlan.introKey())) {
                publicarPanelRoles(tc, locale);
            } else {
                fijarIntro(tc, chPlan, locale);
            }
            creado = tc;
        }
        aplicarConfig(guild, creado, chPlan);
        return creado;
    }

    /**
     * Crea un canal de media (galería). Si Discord no permite media en esta guild (error 50024,
     * «Cannot execute action on this channel type»), cae a un canal de <b>foro</b>, que sí funciona
     * y da una galería por publicaciones equivalente. Sincroniza permisos con la categoría.
     */
    private GuildChannel crearMediaOForo(Category categoria, CanalPlan chPlan) {
        Guild guild = categoria.getGuild();
        List<ForumTagData> tags = chPlan.etiquetas().stream().map(ForumTagData::new).toList();
        try {
            // Se crea a nivel de servidor y se mueve a la categoría: crear media directamente bajo
            // la categoría (category.createMediaChannel) daba 50024 en algunas guilds. El topic y el
            // sync se aplican después, sin tumbar el canal si esos pasos fallan.
            var accionMedia = guild.createMediaChannel(chPlan.nombre())
                    .setDefaultReaction(reaccionDe(chPlan.nombre()));
            if (!tags.isEmpty()) {
                accionMedia = accionMedia.setAvailableTags(tags);
            }
            MediaChannel mc = accionMedia.complete();
            try {
                mc.getManager().setParent(categoria).complete();
                mc.getManager().sync().complete();
                if (chPlan.topic() != null) {
                    mc.getManager().setTopic(chPlan.topic()).complete();
                }
            } catch (RuntimeException ajuste) {
                log.warn("Canal de media {} creado, pero falló mover/topic/sync: {}",
                        chPlan.nombre(), ajuste.getMessage());
            }
            publicarPostsIniciales(mc, chPlan.nombre());
            return mc;
        } catch (RuntimeException e) {
            // Discord no deja a los bots crear canales de media por token de bot (error 50024, igual
            // que antes con los foros). Un canal de media ES un foro en vista galería, así que se crea
            // exactamente eso: galería + etiquetas + reacción por defecto + primera publicación.
            log.info("Media por bot no permitida para {}; se crea foro-galería equivalente", chPlan.nombre());
            var accion = categoria.createForumChannel(chPlan.nombre())
                    .setDefaultLayout(ForumChannel.Layout.GALLERY_VIEW)
                    .setDefaultReaction(reaccionDe(chPlan.nombre()));
            if (!tags.isEmpty()) {
                accion = accion.setAvailableTags(tags);
            }
            if (chPlan.topic() != null) {
                accion = accion.setTopic(chPlan.topic());
            }
            ForumChannel fc = accion.complete();
            fc.getManager().sync().complete();
            publicarPostsIniciales(fc, chPlan.nombre());
            return fc;
        }
    }

    /** Deja un canal en solo-lectura: niega escribir a {@code @everyone} y lo permite al Staff. */
    private void aplicarSoloLectura(StandardGuildMessageChannel canal, Role everyone, Role staff) {
        canal.upsertPermissionOverride(everyone).deny(Permission.MESSAGE_SEND).complete();
        if (staff != null) {
            canal.upsertPermissionOverride(staff).grant(Permission.MESSAGE_SEND).complete();
        }
    }

    /** Reacción por defecto temática del foro (fallback 👍 si no está en el mapa). */
    private static Emoji reaccionDe(String nombreCanal) {
        return Emoji.fromUnicode(REACCION_FORO.getOrDefault(nombreCanal, "👍"));
    }

    /** Publica las publicaciones iniciales (si las hay) en un foro o media, cada una etiquetada. */
    private void publicarPostsIniciales(IPostContainer foro, String nombreCanal) {
        List<String[]> posts = POSTS_FORO.get(nombreCanal);
        if (posts == null) {
            return;
        }
        for (String[] p : posts) {
            try {
                ForumPostAction accion = foro.createForumPost(p[0], MessageCreateData.fromContent(p[1]));
                if (!p[2].isEmpty()) {
                    foro.getAvailableTags().stream()
                            .filter(t -> t.getName().equals(p[2]))
                            .findFirst()
                            .ifPresent(tag -> accion.setTags(List.of(tag)));
                }
                accion.complete();
            } catch (RuntimeException e) {
                log.warn("No se pudo crear el post «{}» en {}", p[0], nombreCanal);
            }
        }
    }

    /** Publica en 🚀 empieza-aquí una fila de botones que llevan a los canales clave. */
    private void publicarNavegacion(Guild guild, Map<String, Long> ids, Locale locale) {
        Long empiezaId = ids.get(CANAL_EMPIEZA);
        TextChannel empieza = (empiezaId == null) ? null : guild.getTextChannelById(empiezaId);
        if (empieza == null) {
            return;
        }
        long guildId = guild.getIdLong();
        List<Button> botones = new java.util.ArrayList<>();
        anadirNav(botones, ids, guildId, "📜・reglas", "📜", "nav.btn.reglas", locale);
        anadirNav(botones, ids, guildId, "🗺️・cómo-funciona", "🗺️", "nav.btn.como", locale);
        anadirNav(botones, ids, guildId, "🎭・roles", "🎭", "nav.btn.roles", locale);
        anadirNav(botones, ids, guildId, "💬・general", "💬", "nav.btn.general", locale);
        anadirNav(botones, ids, guildId, "🎫・soporte", "🎫", "nav.btn.soporte", locale);
        if (botones.isEmpty()) {
            return;
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale,
                        Messages.get(locale, "nav.titulo"), Messages.get(locale, "nav.desc"))
                .setThumbnail(EmbedFactory.iconoUrl())
                .build();
        empieza.sendMessageEmbeds(embed).addActionRow(botones).queue(
                mensaje -> mensaje.pin().queue(),
                error -> log.warn("No se pudo publicar la navegación en {}", empieza.getId(), error));
    }

    /** Añade un botón de enlace a un canal si existe en el mapa de IDs. */
    private static void anadirNav(List<Button> botones, Map<String, Long> ids, long guildId,
                                  String canalNombre, String emoji, String labelKey, Locale locale) {
        Long id = ids.get(canalNombre);
        if (id == null) {
            return;
        }
        String url = "https://discord.com/channels/" + guildId + "/" + id;
        botones.add(Button.link(url, Messages.get(locale, labelKey)).withEmoji(Emoji.fromUnicode(emoji)));
    }

    /** Publica y fija el panel de auto-roles (menús de objetivo y notificaciones) en el canal. */
    private void publicarPanelRoles(TextChannel canal, Locale locale) {
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale,
                Messages.get(locale, "panel.roles.titulo"),
                Messages.get(locale, "panel.roles.desc"))
                .setThumbnail(EmbedFactory.iconoUrl())
                .build();

        StringSelectMenu objetivo = StringSelectMenu.create("roles:objetivo")
                .setPlaceholder(Messages.get(locale, "panel.roles.objetivo.placeholder"))
                .addOption(Messages.get(locale, "config.campo.rol.fuerza"), "FUERZA", Emoji.fromUnicode("💪"))
                .addOption(Messages.get(locale, "config.campo.rol.cardio"), "CARDIO", Emoji.fromUnicode("🏃"))
                .addOption(Messages.get(locale, "config.campo.rol.perdidapeso"), "PERDIDA_PESO", Emoji.fromUnicode("⚖️"))
                .addOption(Messages.get(locale, "config.campo.rol.general"), "GENERAL", Emoji.fromUnicode("🌟"))
                .build();

        StringSelectMenu notif = StringSelectMenu.create("roles:notificaciones")
                .setPlaceholder(Messages.get(locale, "panel.roles.notif.placeholder"))
                .setMinValues(0)
                .setMaxValues(2)
                .addOption(Messages.get(locale, "panel.roles.notif.avisos"), "AVISOS", Emoji.fromUnicode("📣"))
                .addOption(Messages.get(locale, "panel.roles.notif.retos"), "RETOS", Emoji.fromUnicode("🎯"))
                .build();

        canal.sendMessageEmbeds(embed).addActionRow(objetivo).addActionRow(notif).queue(
                mensaje -> mensaje.pin().queue(),
                error -> log.warn("No se pudo publicar el panel de roles en {}", canal.getId(), error));
    }

    /** Publica y fija un embed de ayuda en el canal, si el plan define un {@code introKey}. */
    private void fijarIntro(GuildMessageChannel canal, CanalPlan chPlan, Locale locale) {
        if (chPlan.introKey() == null) {
            return;
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale,
                Messages.get(locale, chPlan.introKey() + ".titulo"),
                Messages.get(locale, chPlan.introKey() + ".desc"))
                .setThumbnail(EmbedFactory.iconoUrl())
                .build();
        canal.sendMessageEmbeds(embed).queue(
                mensaje -> mensaje.pin().queue(),
                error -> log.warn("No se pudo fijar la intro en {}", canal.getId(), error));
    }

    /**
     * ¿Corresponde el canal existente al del plan? Normalmente por nombre exacto, pero los canales
     * de stats («👥 Miembros: …») los renombra {@link com.gymprofit.bot.services.EstadisticasService}
     * con el número real, así que se casan por el prefijo estable (hasta «:») para no duplicarlos al
     * reejecutar {@code /setup}.
     */
    private static boolean mismoCanal(GuildChannel canal, CanalPlan chPlan) {
        String nombre = chPlan.nombre();
        int dosPuntos = nombre.indexOf(':');
        if (dosPuntos > 0 && nombre.endsWith("…")) {
            return canal.getName().startsWith(nombre.substring(0, dosPuntos + 1));
        }
        return canal.getName().equals(nombre);
    }

    /** Reutiliza el canal-ancla por nombre si existe, o lo crea. Nunca se borra en {@code /setup}. */
    private TextChannel reusarOCrearAncla(Guild guild, String nombre) {
        TextChannel existente = primerCanal(guild, nombre);
        return existente != null ? existente : guild.createTextChannel(nombre).complete();
    }

    /**
     * Ancla los ajustes de comunidad a los canales dedicados: reglas → ancla de reglas;
     * actualizaciones y seguridad → ancla de avisos. Al usar canales distintos para reglas y
     * actualizaciones se evita el rechazo de Discord al reasignar. Libera los canales de contenido
     * viejos para poder borrarlos.
     */
    private void anclarComunidad(Guild guild, TextChannel reglas, TextChannel avisos) {
        try {
            guild.getManager()
                    .setRulesChannel(reglas)
                    .setCommunityUpdatesChannel(avisos)
                    .setSafetyAlertsChannel(avisos)
                    .complete();
        } catch (RuntimeException e) {
            log.warn("No se pudieron anclar los canales de comunidad", e);
        }
    }

    /**
     * Recoloca las anclas dentro de la categoría STAFF y las oculta: solo las ven el bot y el
     * Fundador (Admin, que ve todo). Deniega {@code VIEW_CHANNEL} a {@code @everyone} y a Staff.
     */
    private void colocarAnclas(Guild guild, Map<String, Role> roles) {
        Category staff = guild.getCategoriesByName(CAT_STAFF, false).stream().findFirst().orElse(null);
        Role everyone = guild.getPublicRole();
        Role staffRol = roles.get(ROL_STAFF);
        Member yo = guild.getSelfMember();
        for (String nombre : List.of(ANCLA_REGLAS, ANCLA_AVISOS)) {
            TextChannel ancla = primerCanal(guild, nombre);
            if (ancla == null) {
                continue;
            }
            try {
                if (staff != null) {
                    ancla.getManager().setParent(staff).complete();
                }
                ancla.upsertPermissionOverride(everyone).deny(Permission.VIEW_CHANNEL).complete();
                if (staffRol != null) {
                    ancla.upsertPermissionOverride(staffRol).deny(Permission.VIEW_CHANNEL).complete();
                }
                ancla.upsertPermissionOverride(yo).grant(Permission.VIEW_CHANNEL).complete();
            } catch (RuntimeException e) {
                log.warn("No se pudo colocar/ocultar el ancla {}", nombre, e);
            }
        }
    }

    /** Primer canal de texto con ese nombre exacto, o {@code null}. */
    private static TextChannel primerCanal(Guild guild, String nombre) {
        return guild.getTextChannelsByName(nombre, false).stream().findFirst().orElse(null);
    }

    /**
     * Configura la pantalla de bienvenida (Welcome Screen): descripción + hasta 5 canales sugeridos
     * con emoji. Es lo único de la «incorporación» que expone la API (el onboarding con preguntas es
     * manual). Requiere que el servidor sea Comunidad; si no, se omite.
     */
    private void configurarBienvenida(Guild guild) {
        if (!guild.getFeatures().contains("COMMUNITY")) {
            return;
        }
        List<GuildWelcomeScreen.Channel> canales = new java.util.ArrayList<>();
        anadirBienvenida(canales, guild, CANAL_EMPIEZA, "🚀", "bienvenida.canal.empieza");
        anadirBienvenida(canales, guild, "📜・reglas", "📜", "bienvenida.canal.reglas");
        anadirBienvenida(canales, guild, "🎭・roles", "🎭", "bienvenida.canal.roles");
        anadirBienvenida(canales, guild, "💬・general", "💬", "bienvenida.canal.general");
        anadirBienvenida(canales, guild, "🎫・soporte", "🎫", "bienvenida.canal.soporte");
        if (canales.isEmpty()) {
            return;
        }
        try {
            guild.modifyWelcomeScreen()
                    .setEnabled(true)
                    .setDescription(Messages.get(Messages.ES, "bienvenida.descripcion"))
                    .setWelcomeChannels(canales)
                    .complete();
        } catch (RuntimeException e) {
            log.warn("No se pudo configurar la pantalla de bienvenida", e);
        }
    }

    /** Fija «💤 AFK» como canal AFK del servidor con timeout de 5 min, si el canal existe. */
    private void configurarAfk(Guild guild) {
        VoiceChannel afk = guild.getVoiceChannelsByName("💤 AFK", false)
                .stream().findFirst().orElse(null);
        if (afk == null) {
            return;
        }
        try {
            guild.getManager().setAfkChannel(afk).setAfkTimeout(Guild.Timeout.SECONDS_300).complete();
        } catch (RuntimeException e) {
            log.warn("No se pudo fijar el canal AFK", e);
        }
    }

    /** Añade un canal a la pantalla de bienvenida si existe (nombre exacto). */
    private static void anadirBienvenida(List<GuildWelcomeScreen.Channel> lista, Guild guild,
                                         String canalNombre, String emoji, String descKey) {
        TextChannel canal = primerCanal(guild, canalNombre);
        if (canal == null) {
            return;
        }
        lista.add(GuildWelcomeScreen.Channel.of(canal, Messages.get(Messages.ES, descKey),
                Emoji.fromUnicode(emoji)));
    }

    /** Si el canal representa un canal de config, lo guarda en config_servidor. */
    private void aplicarConfig(Guild guild, GuildChannel canal, CanalPlan chPlan) {
        if (chPlan.claveConfig() != null) {
            config.fijarCanal(guild.getIdLong(), chPlan.claveConfig(), canal.getIdLong());
        }
    }

    /**
     * Fija la descripción (topic) de un canal de texto existente si el plan la define y difiere de
     * la actual. Solo aplica a texto (los canales de voz no tienen topic) y evita la llamada REST
     * cuando ya coincide, para no gastar rate limit al reejecutar {@code /setup}.
     */
    private void aplicarTopic(GuildChannel canal, CanalPlan chPlan) {
        if (chPlan.topic() == null) {
            return;
        }
        try {
            // Texto y anuncios comparten StandardGuildMessageChannel; foro y media van aparte.
            if (canal instanceof StandardGuildMessageChannel smc) {
                if (!chPlan.topic().equals(smc.getTopic())) {
                    smc.getManager().setTopic(chPlan.topic()).complete();
                }
            } else if (canal instanceof ForumChannel fc) {
                if (!chPlan.topic().equals(fc.getTopic())) {
                    fc.getManager().setTopic(chPlan.topic()).complete();
                }
            } else if (canal instanceof MediaChannel mc) {
                if (!chPlan.topic().equals(mc.getTopic())) {
                    mc.getManager().setTopic(chPlan.topic()).complete();
                }
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo fijar el topic de {}", canal.getName(), e);
        }
    }

    /**
     * Crea (o reutiliza por nombre) las reglas de AutoMod básicas y manda sus alertas al canal de
     * moderación: anti-menciones masivas, anti-spam y filtro de lenguaje inapropiado (presets de
     * Discord, que ya cubren español). Idempotente: no duplica reglas ya existentes. Devuelve
     * cuántas reglas quedan cubiertas (creadas o ya presentes).
     *
     * <p>Requiere {@code MANAGE_SERVER}; si falta, se omite sin romper el resto del montaje. Los
     * roles de gestión (Admin/Gestionar servidor) quedan exentos automáticamente por Discord.</p>
     */
    private int crearReglasAutoMod(Guild guild) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER)) {
            log.info("Sin MANAGE_SERVER: se omite la creación de reglas de AutoMod");
            return 0;
        }

        TextChannel alertas = guild.getTextChannelsByName(CANAL_MODERACION, false)
                .stream().findFirst().orElse(null);

        Set<String> nombres;
        Set<AutoModTriggerType> tipos;
        try {
            List<AutoModRule> reglas = guild.retrieveAutoModRules().complete();
            nombres = reglas.stream().map(AutoModRule::getName).collect(Collectors.toSet());
            tipos = reglas.stream().map(AutoModRule::getTriggerType).collect(Collectors.toSet());
        } catch (RuntimeException e) {
            log.warn("No se pudieron listar las reglas de AutoMod existentes", e);
            return 0;
        }

        int cubiertas = 0;
        cubiertas += crearReglaAutoMod(guild, nombres, tipos, AUTOMOD_MENCIONES,
                AutoModTriggerType.MENTION_SPAM,
                TriggerConfig.mentionSpam(AUTOMOD_LIMITE_MENCIONES), alertas);
        cubiertas += crearReglaAutoMod(guild, nombres, tipos, AUTOMOD_SPAM,
                AutoModTriggerType.SPAM, TriggerConfig.antiSpam(), alertas);
        cubiertas += crearReglaAutoMod(guild, nombres, tipos, AUTOMOD_LENGUAJE,
                AutoModTriggerType.KEYWORD_PRESET,
                TriggerConfig.presetKeywordFilter(EnumSet.of(
                        AutoModRule.KeywordPreset.PROFANITY,
                        AutoModRule.KeywordPreset.SLURS,
                        AutoModRule.KeywordPreset.SEXUAL_CONTENT)), alertas);
        return cubiertas;
    }

    /**
     * Crea una regla de AutoMod si aún no está cubierta. Los tipos que usamos (mención, spam,
     * preset de palabras) están <b>limitados a 1 regla por servidor</b>, así que se omite si ya
     * existe una con ese nombre <b>o</b> con ese tipo de trigger (aunque la haya creado otro a
     * mano). Respuesta: bloquear el mensaje y, si hay canal de moderación, mandar alerta. Devuelve
     * 1 si queda cubierta (creada o ya existente), 0 si falla la creación.
     */
    private int crearReglaAutoMod(Guild guild, Set<String> nombres, Set<AutoModTriggerType> tipos,
                                  String nombre, AutoModTriggerType tipo, TriggerConfig config,
                                  TextChannel alertas) {
        if (nombres.contains(nombre) || tipos.contains(tipo)) {
            return 1; // ya cubierta (por nombre o por tipo): no duplicar ni exceder el máximo
        }
        try {
            // blockMessage() sin texto custom: no todos los triggers aceptan mensaje personalizado,
            // así se evita un IllegalStateException al crear la regla. El aviso al autor lo pone
            // Discord por defecto; lo importante es la alerta a moderación.
            AutoModRuleData data = AutoModRuleData.onMessage(nombre, config)
                    .putResponses(AutoModResponse.blockMessage());
            if (alertas != null) {
                data.putResponses(AutoModResponse.sendAlert(alertas));
            }
            guild.createAutoModRule(data).complete();
            nombres.add(nombre);
            tipos.add(tipo);
            return 1;
        } catch (RuntimeException e) {
            log.warn("No se pudo crear la regla de AutoMod {}", nombre, e);
            return 0;
        }
    }
}
