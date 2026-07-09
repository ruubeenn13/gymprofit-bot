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
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import java.util.List;
import java.util.Locale;

/**
 * {@code /setup}: monta la estructura del servidor (roles, categorías, canales y permisos según
 * {@link SetupServidorPlan}), purga los mensajes recientes de los canales existentes y
 * autorrellena {@code config_servidor}. Idempotente: reutiliza lo que ya exista (por nombre).
 * Solo admin. Se ejecuta en el hilo de la interacción con llamadas bloqueantes ({@code complete}).
 */
public final class SetupComando implements Comando {

    private static final Logger log = LoggerFactory.getLogger(SetupComando.class);
    private static final String NOMBRE = "setup";

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

        try {
            // Con desde_cero se borra todo antes; entonces no hay mensajes que purgar.
            int limpiados = desdeCero ? vaciarServidor(guild) : purgarCanalesExistentes(guild);
            Role staff = crearRoles(guild);
            int canales = crearCategoriasYCanales(guild, staff);

            var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                    Messages.get(locale, "setup.titulo"),
                    Messages.get(locale, "setup.resumen",
                            SetupServidorPlan.ROLES.size(), canales, limpiados)).build();
            evento.getHook().sendMessageEmbeds(embed).queue();
        } catch (RuntimeException e) {
            log.error("Error en /setup en el servidor {}", guild.getId(), e);
            evento.getHook().sendMessage(Messages.get(locale, "setup.error")).queue();
        }
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
        var self = guild.getSelfMember();
        for (Role rol : List.copyOf(guild.getRoles())) {
            if (rol.isPublicRole() || rol.isManaged() || !self.canInteract(rol)) {
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

    /** Crea (o reutiliza) los roles; devuelve el rol Staff para los permisos de categoría. */
    private Role crearRoles(Guild guild) {
        Role staff = null;
        for (RolPlan plan : SetupServidorPlan.ROLES) {
            Role rol = guild.getRolesByName(plan.nombre(), false).stream().findFirst().orElse(null);
            if (rol == null) {
                var accion = guild.createRole().setName(plan.nombre());
                if (plan.colorRgb() != 0) {
                    accion = accion.setColor(new Color(plan.colorRgb()));
                }
                rol = accion.complete();
            }
            if (plan.objetivo() != null) {
                config.fijarRol(guild.getIdLong(), plan.objetivo(), rol.getIdLong());
            }
            if ("🧹 Staff".equals(plan.nombre())) {
                staff = rol;
            }
        }
        return staff;
    }

    /** Crea (o reutiliza) categorías y canales con permisos; devuelve cuántos canales hay en el plan. */
    private int crearCategoriasYCanales(Guild guild, Role staff) {
        int canales = 0;
        Role everyone = guild.getPublicRole();

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
                }
                categoria = accion.complete();
            }

            for (CanalPlan chPlan : catPlan.canales()) {
                canales++;
                crearCanal(guild, categoria, chPlan, everyone);
            }
        }
        return canales;
    }

    /** Crea (o reutiliza) un canal bajo su categoría, sincroniza permisos y aplica extras. */
    private void crearCanal(Guild guild, Category categoria, CanalPlan chPlan, Role everyone) {
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
            creado = accionVoz.complete();
        } else {
            var accion = categoria.createTextChannel(chPlan.nombre());
            if (chPlan.slowmodeSegundos() > 0) {
                accion = accion.setSlowmode(chPlan.slowmodeSegundos());
            }
            TextChannel tc = accion.complete();
            // Sincroniza permisos con la categoría (hereda ocultación/staff).
            tc.getManager().sync(categoria).complete();
            if (chPlan.soloLectura()) {
                tc.upsertPermissionOverride(everyone)
                        .deny(Permission.MESSAGE_SEND).complete();
            }
            creado = tc;
        }
        aplicarConfig(guild, creado, chPlan);
    }

    /** Si el canal representa un canal de config, lo guarda en config_servidor. */
    private void aplicarConfig(Guild guild, GuildChannel canal, CanalPlan chPlan) {
        if (chPlan.claveConfig() != null) {
            config.fijarCanal(guild.getIdLong(), chPlan.claveConfig(), canal.getIdLong());
        }
    }
}
