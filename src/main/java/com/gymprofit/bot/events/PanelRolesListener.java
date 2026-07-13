package com.gymprofit.bot.events;

import com.gymprofit.bot.db.ConfigServidor;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.ConfigServidorService.Objetivo;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Auto-roles por menú en el canal 🎭 roles (panel que publica {@code /setup}). Permite a cualquiera
 * cogerse su rol de objetivo y sus roles de notificación sin pasar por el onboarding.
 *
 * <ul>
 *   <li>{@code roles:objetivo} — asigna el rol de objetivo configurado y quita los otros tres.</li>
 *   <li>{@code roles:notificaciones} — activa/desactiva 📣 Avisos y 🎯 Retos según la selección.</li>
 * </ul>
 */
public final class PanelRolesListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(PanelRolesListener.class);

    private static final String MENU_OBJETIVO = "roles:objetivo";
    private static final String MENU_NOTIF = "roles:notificaciones";
    private static final String ROL_AVISOS = "📣 Avisos";
    private static final String ROL_RETOS = "🎯 Retos";

    private final ConfigServidorService config;

    public PanelRolesListener(ConfigServidorService config) {
        this.config = config;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evento) {
        if (evento.getGuild() == null) {
            return;
        }
        if (MENU_OBJETIVO.equals(evento.getComponentId())) {
            manejarObjetivo(evento);
        } else if (MENU_NOTIF.equals(evento.getComponentId())) {
            manejarNotificaciones(evento);
        }
    }

    private void manejarObjetivo(StringSelectInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        Objetivo seleccionado = Objetivo.valueOf(evento.getValues().get(0));
        ConfigServidor cfg = config.obtener(evento.getGuild().getIdLong());

        Long rolId = ConfigServidorService.rolDe(cfg, seleccionado);
        Role rol = (rolId == null) ? null : evento.getGuild().getRoleById(rolId);
        if (rol == null) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "roles.objetivo.noconfig"))).setEphemeral(true).queue();
            return;
        }

        // Quita los otros objetivos para que solo tenga uno.
        List<Role> quitar = new ArrayList<>();
        for (Objetivo otro : Objetivo.values()) {
            if (otro == seleccionado) {
                continue;
            }
            Long otroId = ConfigServidorService.rolDe(cfg, otro);
            Role otroRol = (otroId == null) ? null : evento.getGuild().getRoleById(otroId);
            if (otroRol != null) {
                quitar.add(otroRol);
            }
        }

        evento.deferReply(true).queue();
        evento.getGuild().modifyMemberRoles(evento.getMember(), List.of(rol), quitar).queue(
                ok -> evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "roles.objetivo.ok", rol.getName()))).queue(),
                error -> {
                    log.error("No se pudo asignar el objetivo en {}", evento.getGuild().getId(), error);
                    evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "roles.error"))).queue();
                });
    }

    private void manejarNotificaciones(StringSelectInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        List<String> seleccion = evento.getValues();

        List<Role> anadir = new ArrayList<>();
        List<Role> quitar = new ArrayList<>();
        clasificar(rolPorNombre(evento.getGuild(), ROL_AVISOS), seleccion.contains("AVISOS"), anadir, quitar);
        clasificar(rolPorNombre(evento.getGuild(), ROL_RETOS), seleccion.contains("RETOS"), anadir, quitar);

        evento.deferReply(true).queue();
        evento.getGuild().modifyMemberRoles(evento.getMember(), anadir, quitar).queue(
                ok -> evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "roles.notif.ok"))).queue(),
                error -> {
                    log.error("No se pudo actualizar notificaciones en {}", evento.getGuild().getId(), error);
                    evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ANUNCIO, locale, Messages.get(locale, "roles.error"))).queue();
                });
    }

    private static void clasificar(Role rol, boolean querido, List<Role> anadir, List<Role> quitar) {
        if (rol == null) {
            return;
        }
        if (querido) {
            anadir.add(rol);
        } else {
            quitar.add(rol);
        }
    }

    private static Role rolPorNombre(Guild guild, String nombre) {
        return guild.getRolesByName(nombre, false).stream().findFirst().orElse(null);
    }
}
