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
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    /** Permisos que niega el rol Silenciado en todo el servidor. */
    private static final long DENY_SILENCIADO = Permission.getRaw(
            Permission.MESSAGE_SEND, Permission.MESSAGE_ADD_REACTION, Permission.VOICE_SPEAK);

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

        // Trabajo pesado en un hilo aparte para no bloquear el gateway (evita perder heartbeats).
        CompletableFuture.runAsync(() -> {
            try {
                int limpiados = desdeCero ? vaciarServidor(guild) : purgarCanalesExistentes(guild);
                Map<String, Role> roles = crearRoles(guild);
                int canales = crearCategoriasYCanales(guild, roles, locale);

                var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                        Messages.get(locale, "setup.titulo"),
                        Messages.get(locale, "setup.resumen",
                                SetupServidorPlan.ROLES.size(), canales, limpiados)).build();
                evento.getHook().sendMessageEmbeds(embed).queue();
            } catch (RuntimeException e) {
                log.error("Error en /setup en el servidor {}", guild.getId(), e);
                evento.getHook().sendMessage(Messages.get(locale, "setup.error")).queue();
            }
        });
    }

    /**
     * Vacía el servidor: borra todos los canales y los roles borrables (no {@code @everyone}, no
     * gestionados, no por encima del bot). Devuelve cuántos elementos se borraron. Irreversible.
     */
    private int vaciarServidor(Guild guild) {
        int borrados = 0;
        for (GuildChannel canal : List.copyOf(guild.getChannels())) {
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

    /** Crea (o reutiliza) los roles; devuelve un mapa nombre → rol. */
    private Map<String, Role> crearRoles(Guild guild) {
        Map<String, Role> creados = new HashMap<>();
        for (RolPlan plan : SetupServidorPlan.ROLES) {
            Role rol = guild.getRolesByName(plan.nombre(), false).stream().findFirst().orElse(null);
            if (rol == null) {
                var accion = guild.createRole().setName(plan.nombre());
                if (plan.colorRgb() != 0) {
                    accion = accion.setColor(new Color(plan.colorRgb()));
                }
                rol = accion.complete();
            }
            creados.put(plan.nombre(), rol);
            if (plan.objetivo() != null) {
                config.fijarRol(guild.getIdLong(), plan.objetivo(), rol.getIdLong());
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

        for (CategoriaPlan catPlan : SetupServidorPlan.CATEGORIAS) {
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
                    // El propio bot debe poder ver la categoría oculta para crear/gestionar dentro
                    // (si no, al estar en @everyone se auto-bloquea).
                    accion = accion.addMemberPermissionOverride(guild.getSelfMember().getIdLong(),
                            Permission.VIEW_CHANNEL.getRawValue(), 0L);
                }
                if (silenciado != null) {
                    accion = accion.addRolePermissionOverride(silenciado.getIdLong(), 0L, DENY_SILENCIADO);
                }
                categoria = accion.complete();
            }

            for (CanalPlan chPlan : catPlan.canales()) {
                canales++;
                crearCanal(guild, categoria, chPlan, everyone, locale);
            }
        }
        return canales;
    }

    /** Crea (o reutiliza) un canal bajo su categoría, sincroniza permisos, fija intro y config. */
    private void crearCanal(Guild guild, Category categoria, CanalPlan chPlan, Role everyone,
                            Locale locale) {
        GuildChannel existente = guild.getChannels().stream()
                .filter(c -> c.getName().equals(chPlan.nombre()))
                .findFirst().orElse(null);
        if (existente != null) {
            aplicarConfig(guild, existente, chPlan);
            return;
        }

        GuildChannel creado;
        if (chPlan.tipo() == SetupServidorPlan.TipoCanalDiscord.VOZ) {
            var accionVoz = categoria.createVoiceChannel(chPlan.nombre());
            if (chPlan.limiteVoz() > 0) {
                accionVoz = accionVoz.setUserlimit(chPlan.limiteVoz());
            }
            VoiceChannel vc = accionVoz.complete();
            vc.getManager().sync().complete();
            creado = vc;
        } else {
            var accion = categoria.createTextChannel(chPlan.nombre());
            if (chPlan.slowmodeSegundos() > 0) {
                accion = accion.setSlowmode(chPlan.slowmodeSegundos());
            }
            TextChannel tc = accion.complete();
            // Hereda los permisos de la categoría (ocultación, staff, silenciado).
            tc.getManager().sync().complete();
            if (chPlan.soloLectura()) {
                tc.upsertPermissionOverride(everyone).deny(Permission.MESSAGE_SEND).complete();
            }
            fijarIntro(tc, chPlan, locale);
            creado = tc;
        }
        aplicarConfig(guild, creado, chPlan);
    }

    /** Publica y fija un embed de ayuda en el canal, si el plan define un {@code introKey}. */
    private void fijarIntro(TextChannel canal, CanalPlan chPlan, Locale locale) {
        if (chPlan.introKey() == null) {
            return;
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ANUNCIO, locale,
                Messages.get(locale, chPlan.introKey() + ".titulo"),
                Messages.get(locale, chPlan.introKey() + ".desc")).build();
        canal.sendMessageEmbeds(embed).queue(
                mensaje -> mensaje.pin().queue(),
                error -> log.warn("No se pudo fijar la intro en {}", canal.getId(), error));
    }

    /** Si el canal representa un canal de config, lo guarda en config_servidor. */
    private void aplicarConfig(Guild guild, GuildChannel canal, CanalPlan chPlan) {
        if (chPlan.claveConfig() != null) {
            config.fijarCanal(guild.getIdLong(), chPlan.claveConfig(), canal.getIdLong());
        }
    }
}
