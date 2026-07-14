package com.gymprofit.bot.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asigna el rol de {@link Rango} correspondiente al nivel del jugador (F-ECO-3). Idempotente: deja
 * puesto solo el rango correcto y quita los demás. Requiere que existan los roles (los crea
 * {@code /setup}) y que el bot tenga permiso para gestionarlos; si algo falla, se registra y se sigue
 * (no se rompe el flujo). Se llama al subir de nivel y también desde {@code /rank} (auto-corrige).
 */
public final class RangoService {

    private static final Logger log = LoggerFactory.getLogger(RangoService.class);

    /** Deja al miembro solo con el rol de rango que le toca por su nivel. */
    public void sincronizar(Guild guild, Member miembro, int nivel) {
        Rango objetivo = Rango.para(nivel);
        for (Rango r : Rango.values()) {
            Role rol = guild.getRolesByName(r.rolNombre(), false).stream().findFirst().orElse(null);
            if (rol == null) {
                continue;
            }
            boolean deberia = r == objetivo;
            boolean tiene = miembro.getRoles().contains(rol);
            try {
                if (deberia && !tiene) {
                    guild.addRoleToMember(miembro, rol).queue(ok -> {
                    }, err -> log.warn("No se pudo dar el rango {}", r.rolNombre(), err));
                } else if (!deberia && tiene) {
                    guild.removeRoleFromMember(miembro, rol).queue(ok -> {
                    }, err -> log.warn("No se pudo quitar el rango {}", r.rolNombre(), err));
                }
            } catch (RuntimeException e) {
                log.warn("Error sincronizando rango {} de {}", r.rolNombre(), miembro.getId(), e);
            }
        }
    }
}
