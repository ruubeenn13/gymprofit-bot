package com.gymprofit.bot.commands.economia;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.function.LongConsumer;

/**
 * Gestión del canal privado de un gremio (F-ECO-5a) con permisos <b>de miembro</b> (sin rol, para no
 * gastar el cupo de roles). El canal deniega la vista a {@code @everyone} y la concede a cada miembro.
 * Todo es best-effort: si el bot no puede gestionar canales, se registra y se sigue (el gremio existe
 * en BD igualmente).
 */
final class GremioCanal {

    private static final Logger log = LoggerFactory.getLogger(GremioCanal.class);

    /** Permisos que se conceden a cada miembro en su canal de gremio. */
    private static final EnumSet<Permission> PERMISOS = EnumSet.of(
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY);

    private GremioCanal() {
    }

    /** Crea el canal privado y devuelve su id por callback (solo si se creó). */
    static void crear(Guild guild, String nombre, long duenoId, LongConsumer onCreado) {
        guild.createTextChannel("🏰・" + nombre)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addMemberPermissionOverride(duenoId, PERMISOS, null)
                .queue(canal -> onCreado.accept(canal.getIdLong()),
                        err -> log.warn("No se pudo crear el canal del gremio {}", nombre, err));
    }

    /** Da acceso al canal a un miembro. */
    static void anadir(Guild guild, Long canalId, long miembroId) {
        TextChannel canal = canal(guild, canalId);
        if (canal == null) {
            return;
        }
        guild.retrieveMemberById(miembroId).queue(
                m -> canal.upsertPermissionOverride(m).grant(PERMISOS).queue(null, e -> {
                }),
                e -> { });
    }

    /** Quita el acceso al canal a un miembro. */
    static void quitar(Guild guild, Long canalId, long miembroId) {
        TextChannel canal = canal(guild, canalId);
        if (canal == null) {
            return;
        }
        guild.retrieveMemberById(miembroId).queue(m -> {
            var ov = canal.getPermissionOverride(m);
            if (ov != null) {
                ov.delete().queue(null, e -> { });
            }
        }, e -> { });
    }

    /** Borra el canal del gremio. */
    static void eliminar(Guild guild, Long canalId) {
        TextChannel canal = canal(guild, canalId);
        if (canal != null) {
            canal.delete().queue(null, e -> { });
        }
    }

    private static TextChannel canal(Guild guild, Long canalId) {
        return canalId == null ? null : guild.getTextChannelById(canalId);
    }
}
