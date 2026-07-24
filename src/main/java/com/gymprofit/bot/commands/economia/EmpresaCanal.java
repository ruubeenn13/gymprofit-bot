package com.gymprofit.bot.commands.economia;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.function.LongConsumer;

/**
 * Gestión del canal privado de una empresa (F4, espejo exacto de {@link GremioCanal}) con permisos
 * <b>de miembro</b> (sin rol, para no gastar el cupo de roles): el canal deniega la vista a
 * {@code @everyone} y la concede a cada miembro. Todo es best-effort: si el bot no puede gestionar
 * canales, se registra y se sigue (la empresa existe en BD igualmente).
 *
 * <p>Es <b>pública</b> (a diferencia de {@link GremioCanal}, que solo se usa dentro de su paquete)
 * porque la sincronización BD↔Discord de las empresas se dispara desde dos sitios: el comando
 * {@link EmpresaComando} (fundar/info) y el
 * {@link com.gymprofit.bot.events.EmpresaBotonesListener listener} de botones (ingreso, disolución y
 * sacar/despedir por voto), que vive en otro paquete y necesita ver estos métodos.
 */
public final class EmpresaCanal {

    private static final Logger log = LoggerFactory.getLogger(EmpresaCanal.class);

    /** Permisos que se conceden a cada miembro en su canal de empresa. */
    private static final EnumSet<Permission> PERMISOS = EnumSet.of(
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY);

    private EmpresaCanal() {
    }

    /** Crea el canal privado y devuelve su id por callback (solo si se creó). */
    public static void crear(Guild guild, String nombre, long duenoId, LongConsumer onCreado) {
        guild.createTextChannel("🏢・" + nombre)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addMemberPermissionOverride(duenoId, PERMISOS, null)
                .queue(canal -> onCreado.accept(canal.getIdLong()),
                        err -> log.warn("No se pudo crear el canal de la empresa {}", nombre, err));
    }

    /** Da acceso al canal a un miembro. */
    public static void anadir(Guild guild, Long canalId, long miembroId) {
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
    public static void quitar(Guild guild, Long canalId, long miembroId) {
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

    /** Borra el canal de la empresa. */
    public static void eliminar(Guild guild, Long canalId) {
        TextChannel canal = canal(guild, canalId);
        if (canal != null) {
            canal.delete().queue(null, e -> { });
        }
    }

    private static TextChannel canal(Guild guild, Long canalId) {
        return canalId == null ? null : guild.getTextChannelById(canalId);
    }
}
