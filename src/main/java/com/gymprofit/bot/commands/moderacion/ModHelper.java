package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.db.Sancion;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.util.Duraciones;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Utilidades compartidas por los comandos de moderación: control de acceso por rol
 * («altos cargos»), comprobación de jerarquía y publicación de la acción en {@code bot-logs}.
 */
public final class ModHelper {

    /** Roles autorizados a usar los comandos de moderación (por nombre, ver {@code SetupServidorPlan}). */
    public static final Set<String> ROLES_ALTOS = Set.of("🧹 Staff", "🛡️ Admin", "👑 Fundador");

    private ModHelper() {
    }

    /** {@code true} si el miembro tiene alguno de los roles de alto cargo. */
    public static boolean esAltoCargo(Member miembro) {
        return miembro != null
                && miembro.getRoles().stream().anyMatch(r -> ROLES_ALTOS.contains(r.getName()));
    }

    /**
     * ¿Puede {@code actor} moderar a {@code objetivo}? No a sí mismo, no al dueño, y solo si su rol
     * está por encima (jerarquía). Si {@code objetivo} es {@code null} (p. ej. ban por id de alguien
     * que no está en el servidor) se permite.
     */
    public static boolean puedeModerar(Member actor, Member objetivo) {
        if (objetivo == null) {
            return true;
        }
        if (actor.getId().equals(objetivo.getId()) || objetivo.isOwner()) {
            return false;
        }
        return actor.canInteract(objetivo);
    }

    /** Nombre del rol de silencio que crea {@code /setup}. */
    public static final String ROL_SILENCIADO = "🔇 Silenciado";

    /** Sanciones por página en {@code /modlogs}. */
    public static final int SANCIONES_POR_PAGINA = 10;

    /** Emoji por tipo de sanción (para el historial). */
    private static final Map<String, String> EMOJI_TIPO = Map.of(
            "WARN", "⚠️", "MUTE", "🔇", "UNMUTE", "🔊", "TIMEOUT", "⏳",
            "UNTIMEOUT", "✅", "KICK", "👢", "BAN", "🔨", "UNBAN", "✅", "NICK", "✏️");

    /** Formatea una página de sanciones como líneas para la descripción del embed. */
    public static String formatearSanciones(Locale locale, List<Sancion> sanciones) {
        StringBuilder sb = new StringBuilder();
        for (Sancion s : sanciones) {
            String emoji = EMOJI_TIPO.getOrDefault(s.tipo(), "•");
            String extra = s.duracionSeg() != null ? " · " + Duraciones.formatear(s.duracionSeg()) : "";
            String motivo = s.motivo() != null ? " · " + s.motivo() : "";
            sb.append(Messages.get(locale, "modlogs.linea",
                    s.id(), emoji, s.tipo(), motivo + extra,
                    "<@" + s.moderadorId() + ">",
                    "<t:" + s.creadoEn().getEpochSecond() + ":R>")).append('\n');
        }
        return sb.toString();
    }

    /** Construye un embed de moderación (tono serio). */
    public static MessageEmbed embed(Locale locale, String titulo, String descripcion) {
        return EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale, titulo, descripcion).build();
    }

    /** Rol {@code 🔇 Silenciado} del servidor, o {@code null} si no existe (falta ejecutar /setup). */
    public static Role rolSilenciado(Guild guild) {
        return guild.getRolesByName(ROL_SILENCIADO, false).stream().findFirst().orElse(null);
    }

    /**
     * Bloquea o desbloquea un canal para {@code @everyone}: al bloquear niega MESSAGE_SEND; al
     * desbloquear limpia el override (restaura la herencia de la categoría). Idempotente.
     */
    public static void aplicarBloqueo(IPermissionContainer canal, boolean bloquear, String razon) {
        Role everyone = canal.getGuild().getPublicRole();
        var accion = canal.upsertPermissionOverride(everyone);
        if (bloquear) {
            accion.deny(Permission.MESSAGE_SEND).reason(razon).queue();
        } else {
            accion.clear(Permission.MESSAGE_SEND).reason(razon).queue();
        }
    }

    /** Publica el embed de la acción en el canal {@code bot-logs} del servidor si está configurado. */
    public static void registrarEnLogs(Guild guild, ConfigServidorService config, MessageEmbed embed) {
        Long canalId = config.obtener(guild.getIdLong()).canalBotLogs();
        if (canalId == null) {
            return;
        }
        TextChannel canal = guild.getTextChannelById(canalId);
        if (canal != null) {
            canal.sendMessageEmbeds(embed).queue(null, error -> { });
        }
    }
}
