package com.gymprofit.bot.commands.moderacion;

import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.ModeracionService;
import com.gymprofit.bot.services.ModeracionService.AccionEscalado;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Aplica una amonestación completa y reutilizable: registra el aviso (vía {@link ModeracionService}),
 * ejecuta sobre Discord el <b>escalado automático</b> que corresponda (timeout 1 h / 24 h o ban),
 * publica la acción en {@code bot-logs} y avisa por DM al sancionado (best-effort).
 *
 * <p>Se extrajo de {@code WarnComando} para poder disparar la misma amonestación desde otros puntos
 * (p. ej. automoderación) sin duplicar la lógica de escalado, log y aviso. Los textos del log y del
 * DM van en español (comunidad ES-first); el comando que invoca decide cómo localizar su propia
 * respuesta al staff con {@link Resultado#escaladoClave()}.</p>
 */
public final class AplicadorSanciones {

    private static final Logger log = LoggerFactory.getLogger(AplicadorSanciones.class);

    /** Razón que Discord adjunta al timeout/ban del escalado (audit log del servidor). */
    static final String RAZON_ESCALADO = "Escalado automático por acumulación de avisos";

    private final ModeracionService moderacion;
    private final ConfigServidorService config;

    public AplicadorSanciones(ModeracionService moderacion, ConfigServidorService config) {
        this.moderacion = moderacion;
        this.config = config;
    }

    /** El aviso base y la clave i18n del escalón aplicado (o {@code null} si no hubo escalado). */
    public record Resultado(ModeracionService.ResultadoAviso aviso, String escaladoClave) {
    }

    /**
     * Amonesta a {@code objetivo}: registra el aviso, aplica el escalado, lo publica en logs y avisa
     * por DM. {@code objetivo} es el {@link Member} (puede tener rol/jerarquía); el motivo puede ser
     * {@code null}.
     */
    public Resultado aplicar(Guild guild, Member objetivo, long moderadorId, String motivo) {
        var r = moderacion.avisar(guild.getIdLong(), objetivo.getIdLong(), moderadorId, motivo);
        String escaladoClave = aplicarEscalado(guild, objetivo, moderadorId, r.accion());
        registrarEnLogs(guild, objetivo.getUser(), r, motivo, escaladoClave);
        avisarPorDm(guild, objetivo.getUser(), r.accion(), motivo);
        return new Resultado(r, escaladoClave);
    }

    /**
     * Ejecuta el escalón sobre Discord y lo anota en el historial (atribuido a {@code moderadorId},
     * como en la lógica original de {@code /warn}). Devuelve la clave i18n del escalón aplicado (que el
     * comando localiza para su respuesta), o {@code null} si no hubo escalado.
     */
    private String aplicarEscalado(Guild guild, Member objetivo, long moderadorId, AccionEscalado accion) {
        switch (accion) {
            case TIMEOUT_1H -> {
                aplicarTimeout(guild, objetivo, moderadorId, ModeracionService.TIMEOUT_1H_SEG);
                return "warn.escalado.timeout1h";
            }
            case TIMEOUT_24H -> {
                aplicarTimeout(guild, objetivo, moderadorId, ModeracionService.TIMEOUT_24H_SEG);
                return "warn.escalado.timeout24h";
            }
            case BAN -> {
                guild.ban(objetivo.getUser(), 0, TimeUnit.SECONDS).reason(RAZON_ESCALADO).queue();
                moderacion.registrar(guild.getIdLong(), objetivo.getIdLong(), moderadorId,
                        "BAN", RAZON_ESCALADO, null, null);
                return "warn.escalado.ban";
            }
            default -> {
                return null;
            }
        }
    }

    private void aplicarTimeout(Guild guild, Member objetivo, long moderadorId, long segundos) {
        objetivo.timeoutFor(Duration.ofSeconds(segundos)).reason(RAZON_ESCALADO).queue();
        moderacion.registrar(guild.getIdLong(), objetivo.getIdLong(), moderadorId,
                "TIMEOUT", RAZON_ESCALADO, null, segundos);
    }

    /** Publica en {@code bot-logs} un embed serio (ES) con el aviso y el escalado aplicado. */
    private void registrarEnLogs(Guild guild, User objetivo, ModeracionService.ResultadoAviso aviso,
                                 String motivo, String escaladoClave) {
        String desc = Messages.get(Messages.ES, "warn.hecho",
                objetivo.getAsMention(), aviso.warnsActivos());
        if (motivo != null) {
            desc += "\n" + Messages.get(Messages.ES, "warn.motivo", motivo);
        }
        if (escaladoClave != null) {
            desc += "\n" + Messages.get(Messages.ES, escaladoClave);
        }
        MessageEmbed embed = ModHelper.embed(Messages.ES, Messages.get(Messages.ES, "warn.titulo"), desc);
        ModHelper.registrarEnLogs(guild, config, embed);
    }

    /** Avisa por DM al sancionado (best-effort, ES). Un DM cerrado no debe romper la sanción. */
    private void avisarPorDm(Guild guild, User objetivo, AccionEscalado accion, String motivo) {
        String clave = switch (accion) {
            case BAN -> "sancion.dm.ban";
            case TIMEOUT_1H, TIMEOUT_24H -> "sancion.dm.timeout";
            default -> "sancion.dm.aviso";
        };
        String motivoTexto = motivo != null ? motivo : Messages.get(Messages.ES, "warns.sinmotivo");
        MessageEmbed embed = ModHelper.embed(Messages.ES,
                Messages.get(Messages.ES, "sancion.dm.titulo"),
                Messages.get(Messages.ES, clave, guild.getName(), motivoTexto));
        objetivo.openPrivateChannel().queue(
                canal -> canal.sendMessageEmbeds(embed).queue(null, e -> { }),
                e -> log.debug("No se pudo enviar el DM de sanción a {}: {}",
                        objetivo.getId(), e.toString()));
    }
}
