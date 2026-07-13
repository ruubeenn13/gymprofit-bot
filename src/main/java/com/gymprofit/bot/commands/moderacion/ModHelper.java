package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.services.ConfigServidorService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Locale;
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

    /** Construye un embed de moderación (tono serio). */
    public static MessageEmbed embed(Locale locale, String titulo, String descripcion) {
        return EmbedFactory.base(EmbedFactory.Tipo.MODERACION, locale, titulo, descripcion).build();
    }

    /** Rol {@code 🔇 Silenciado} del servidor, o {@code null} si no existe (falta ejecutar /setup). */
    public static Role rolSilenciado(Guild guild) {
        return guild.getRolesByName(ROL_SILENCIADO, false).stream().findFirst().orElse(null);
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
