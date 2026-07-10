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
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.automod.AutoModResponse;
import net.dv8tion.jda.api.entities.automod.AutoModRule;
import net.dv8tion.jda.api.entities.automod.build.AutoModRuleData;
import net.dv8tion.jda.api.entities.automod.build.TriggerConfig;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.MediaChannel;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagData;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    /** Canal (solo staff) al que AutoMod manda las alertas de contenido bloqueado. */
    private static final String CANAL_MODERACION = "📋・moderación";
    /** Canales a los que se reapuntan los ajustes de comunidad tras montar (reglas/updates/seguridad). */
    private static final String CANAL_REGLAS = "📜・reglas";
    private static final String CANAL_BOTLOGS = "🤖・bot-logs";
    /** Canal temporal que sostiene los ajustes de comunidad mientras se borran los viejos. */
    private static final String CANAL_TEMPORAL = "setup-temporal";
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
                // seguridad. Se crean-y-reasignan a un canal temporal para poder borrar los viejos;
                // al final se reapuntan a los nuevos y se borra el temporal.
                TextChannel temporal = null;
                int limpiados;
                if (desdeCero) {
                    if (guild.getFeatures().contains("COMMUNITY")) {
                        temporal = guild.createTextChannel(CANAL_TEMPORAL).complete();
                        reasignarComunidad(guild, temporal);
                    }
                    limpiados = vaciarServidor(guild, temporal == null ? 0L : temporal.getIdLong());
                } else {
                    limpiados = purgarCanalesExistentes(guild);
                }
                Map<String, Role> roles = crearRoles(guild);
                int canales = crearCategoriasYCanales(guild, roles, locale);
                int reglasAutoMod = crearReglasAutoMod(guild);

                // Reapunta la comunidad a los canales recién montados y elimina el temporal.
                if (temporal != null) {
                    restaurarComunidad(guild);
                    try {
                        temporal.delete().complete();
                    } catch (RuntimeException e) {
                        log.warn("No se pudo borrar el canal temporal de setup", e);
                    }
                }

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
     * Vacía el servidor: borra todos los canales (salvo {@code exceptoCanalId}) y los roles
     * borrables (no {@code @everyone}, no gestionados, no por encima del bot). Devuelve cuántos
     * elementos se borraron. Irreversible.
     *
     * @param exceptoCanalId canal a conservar (el temporal que sostiene la comunidad), o 0 si ninguno
     */
    private int vaciarServidor(Guild guild, long exceptoCanalId) {
        int borrados = 0;
        for (GuildChannel canal : List.copyOf(guild.getChannels())) {
            // El canal temporal sostiene los ajustes de comunidad; no se borra en esta pasada.
            if (canal.getIdLong() == exceptoCanalId) {
                continue;
            }
            try {
                canal.delete().complete();
                borrados++;
            } catch (RuntimeException e) {
                log.warn("No se pudo borrar el canal {}", canal.getId(), e);
            }
        }
        Member yo = guild.getSelfMember();
        List<Role> misRoles = yo.getRoles();
        for (Role rol : List.copyOf(guild.getRoles())) {
            // No borrar @everyone, roles gestionados, roles por encima del bot, ni roles que el
            // propio bot tenga (borrarlos le quitaría permisos a mitad del montaje).
            if (rol.isPublicRole() || rol.isManaged() || !yo.canInteract(rol) || misRoles.contains(rol)) {
                continue;
            }
            try {
                rol.delete().complete();
                borrados++;
            } catch (RuntimeException e) {
                log.warn("No se pudo borrar el rol {}", rol.getId(), e);
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
                            canal = crearCanal(guild, categoria, chPlan, everyone, locale);
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
                                    Locale locale) {
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
            ForumChannel fc = accion.complete();
            fc.getManager().sync().complete();
            creado = fc;
        } else if (chPlan.tipo() == SetupServidorPlan.TipoCanalDiscord.MEDIA) {
            var accion = categoria.createMediaChannel(chPlan.nombre());
            if (chPlan.topic() != null) {
                accion = accion.setTopic(chPlan.topic());
            }
            MediaChannel mc = accion.complete();
            mc.getManager().sync().complete();
            creado = mc;
        } else if (chPlan.tipo() == SetupServidorPlan.TipoCanalDiscord.ANUNCIOS) {
            var accion = categoria.createNewsChannel(chPlan.nombre());
            if (chPlan.topic() != null) {
                accion = accion.setTopic(chPlan.topic());
            }
            NewsChannel nc = accion.complete();
            nc.getManager().sync().complete();
            // Solo el staff anuncia; @everyone lee pero no escribe.
            if (chPlan.soloLectura()) {
                nc.upsertPermissionOverride(everyone).deny(Permission.MESSAGE_SEND).complete();
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
                tc.upsertPermissionOverride(everyone).deny(Permission.MESSAGE_SEND).complete();
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

    /**
     * Apunta los tres canales de comunidad (reglas, actualizaciones y seguridad) al canal dado.
     * Se usa con un canal temporal antes de vaciar, para que Discord permita borrar los actuales
     * (no deja borrar los canales requeridos por una comunidad).
     */
    private void reasignarComunidad(Guild guild, TextChannel destino) {
        try {
            guild.getManager()
                    .setRulesChannel(destino)
                    .setCommunityUpdatesChannel(destino)
                    .setSafetyAlertsChannel(destino)
                    .complete();
        } catch (RuntimeException e) {
            log.warn("No se pudieron reasignar los canales de comunidad al temporal", e);
        }
    }

    /**
     * Reapunta los canales de comunidad a los canales recién montados: reglas → {@code 📜・reglas},
     * actualizaciones → {@code 🤖・bot-logs}, seguridad → {@code 📋・moderación} (los que existan).
     */
    private void restaurarComunidad(Guild guild) {
        TextChannel reglas = primerCanal(guild, CANAL_REGLAS);
        TextChannel botLogs = primerCanal(guild, CANAL_BOTLOGS);
        TextChannel moderacion = primerCanal(guild, CANAL_MODERACION);
        try {
            var manager = guild.getManager();
            if (reglas != null) {
                manager = manager.setRulesChannel(reglas);
            }
            if (botLogs != null) {
                manager = manager.setCommunityUpdatesChannel(botLogs);
            }
            if (moderacion != null) {
                manager = manager.setSafetyAlertsChannel(moderacion);
            }
            manager.complete();
        } catch (RuntimeException e) {
            log.warn("No se pudieron reapuntar los canales de comunidad a los nuevos", e);
        }
    }

    /** Primer canal de texto con ese nombre exacto, o {@code null}. */
    private static TextChannel primerCanal(Guild guild, String nombre) {
        return guild.getTextChannelsByName(nombre, false).stream().findFirst().orElse(null);
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

        List<String> existentes;
        try {
            existentes = guild.retrieveAutoModRules().complete()
                    .stream().map(AutoModRule::getName).toList();
        } catch (RuntimeException e) {
            log.warn("No se pudieron listar las reglas de AutoMod existentes", e);
            return 0;
        }

        int cubiertas = 0;
        cubiertas += crearReglaAutoMod(guild, existentes, AUTOMOD_MENCIONES,
                TriggerConfig.mentionSpam(AUTOMOD_LIMITE_MENCIONES), alertas);
        cubiertas += crearReglaAutoMod(guild, existentes, AUTOMOD_SPAM,
                TriggerConfig.antiSpam(), alertas);
        cubiertas += crearReglaAutoMod(guild, existentes, AUTOMOD_LENGUAJE,
                TriggerConfig.presetKeywordFilter(EnumSet.of(
                        AutoModRule.KeywordPreset.PROFANITY,
                        AutoModRule.KeywordPreset.SLURS,
                        AutoModRule.KeywordPreset.SEXUAL_CONTENT)), alertas);
        return cubiertas;
    }

    /**
     * Crea una regla de AutoMod si no existe ya una con ese nombre. Respuesta: bloquear el mensaje
     * (con aviso al autor) y, si hay canal de moderación, mandar alerta. Devuelve 1 si la regla
     * queda cubierta (creada o ya existente), 0 si falla la creación.
     */
    private int crearReglaAutoMod(Guild guild, List<String> existentes, String nombre,
                                  TriggerConfig config, TextChannel alertas) {
        if (existentes.contains(nombre)) {
            return 1; // ya existe: idempotencia, no duplicar
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
            return 1;
        } catch (RuntimeException e) {
            log.warn("No se pudo crear la regla de AutoMod {}", nombre, e);
            return 0;
        }
    }
}
