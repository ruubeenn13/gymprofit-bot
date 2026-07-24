package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.economia.EmpresaComando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EmpresaGestionService;
import com.gymprofit.bot.services.EmpresaGestionService.ResultadoVoto;
import com.gymprofit.bot.services.EmpresaService;
import com.gymprofit.bot.services.EmpresaService.ResultadoDisolver;
import com.gymprofit.bot.services.EmpresaService.ResultadoResolver;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Locale;

/**
 * Botones de {@code /empresa}: resolver una pendiente (Aceptar/Rechazar una invitación o
 * Aprobar/Rechazar una solicitud) y confirmar la disolución (Sí/No). Se separa del comando porque JDA
 * enruta los botones por evento, no por el comando que los emitió.
 *
 * <p>Dos familias de customId:</p>
 * <ul>
 *   <li>{@code empresa:resolver:<pendienteId>:<1|0>}: la potestad (que quien pulsa sea la parte con
 *       derecho a resolver) la valida {@link EmpresaService#resolver}; aquí solo se traduce el
 *       resultado. Editar el mensaje quita los botones para que no se repulse.</li>
 *   <li>{@code empresa:disolver:<accion>:<ownerId>}: como la dimisión de {@code /trabajo}, se valida
 *       en el propio botón que quien pulsa es el dueño (el customId lleva su id).</li>
 *   <li>{@code empresa:voto:<propuestaId>:<1|0>} (F2): la potestad (que quien vota sea alto cargo) y el
 *       veredicto los decide {@link EmpresaGestionService#votar}; aquí solo se traduce el resultado y,
 *       cuando la propuesta queda resuelta, se editan los botones para que no se vote dos veces.</li>
 * </ul>
 */
public final class EmpresaBotonesListener extends ListenerAdapter {

    private final EmpresaService empresa;
    private final EmpresaGestionService gestion;

    public EmpresaBotonesListener(EmpresaService empresa, EmpresaGestionService gestion) {
        this.empresa = empresa;
        this.gestion = gestion;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        if (id.startsWith(EmpresaComando.BOTON_RESOLVER + ":")) {
            resolver(evento, locale, id);
        } else if (id.startsWith(EmpresaComando.BOTON_VOTO + ":")) {
            votar(evento, locale, id);
        } else if (id.startsWith(EmpresaComando.BOTON_DISOLVER_SI)
                || id.startsWith(EmpresaComando.BOTON_DISOLVER_NO)) {
            disolver(evento, locale, id);
        }
    }

    /**
     * Registra un voto sobre una propuesta: {@code empresa:voto:<propuestaId>:<1|0>}. Mientras la
     * propuesta siga abierta (voto registrado sin veredicto, o quien pulsa no es alto cargo) se responde
     * <b>efímero</b> y se deja el mensaje intacto para que los demás sigan votando; cuando hay veredicto
     * (aprobada/rechazada/caducada) o la propuesta ya no existe, se edita el mensaje quitando los botones.
     */
    private void votar(ButtonInteractionEvent evento, Locale locale, String id) {
        // customId = "empresa:voto:<propuestaId>:<1|0>".
        String[] partes = id.split(":");
        long propuestaId = Long.parseUnsignedLong(partes[2]);
        boolean si = "1".equals(partes[3]);
        ResultadoVoto r = gestion.votar(propuestaId, evento.getUser().getIdLong(), si);
        // Estados que NO cierran la votación: el mensaje sigue con sus botones para el resto de altos cargos.
        if (r == ResultadoVoto.NO_AUTORIZADO) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.gestion.voto.no_autorizado"))).setEphemeral(true).queue();
            return;
        }
        if (r == ResultadoVoto.REGISTRADO) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.gestion.voto.registrado"))).setEphemeral(true).queue();
            return;
        }
        String desc = switch (r) {
            case APROBADA_EJECUTADA -> Messages.get(locale, "empresa.gestion.voto.aprobada");
            // Aprobada por veredicto pero la acción no se aplicó (ascenso sin fondos o requisitos caídos):
            // se dice claro para no anunciar como hecho lo que no ocurrió.
            case APROBADA_NO_EJECUTADA -> Messages.get(locale, "empresa.gestion.voto.aprobada_no_ejecutada");
            case RECHAZADA -> Messages.get(locale, "empresa.gestion.voto.rechazada");
            case CADUCADA -> Messages.get(locale, "empresa.gestion.voto.caducada");
            case NO_EXISTE -> Messages.get(locale, "empresa.gestion.voto.no_existe");
            case REGISTRADO, NO_AUTORIZADO -> throw new IllegalStateException("estado abierto ya tratado");
        };
        evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, desc))
                .setComponents().queue();
    }

    /** Resuelve una pendiente: {@code empresa:resolver:<pendienteId>:<1|0>}. */
    private void resolver(ButtonInteractionEvent evento, Locale locale, String id) {
        // customId = "empresa:resolver:<pendienteId>:<1|0>".
        String[] partes = id.split(":");
        long pendienteId = Long.parseUnsignedLong(partes[2]);
        boolean aceptar = "1".equals(partes[3]);
        ResultadoResolver r = empresa.resolver(pendienteId, aceptar, evento.getUser().getIdLong());
        // NO_ERES_PARTE no toca el mensaje (otro podría resolverlo aún): se avisa efímero y se sale.
        if (r == ResultadoResolver.NO_ERES_PARTE) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.resolver.no_eres_parte"))).setEphemeral(true).queue();
            return;
        }
        String desc = switch (r) {
            case ACEPTADO -> Messages.get(locale, "empresa.resolver.aceptado");
            case RECHAZADO -> Messages.get(locale, "empresa.resolver.rechazado");
            case PENDIENTE_NO_EXISTE -> Messages.get(locale, "empresa.resolver.pendiente_no_existe");
            case YA_EN_EMPRESA -> Messages.get(locale, "empresa.resolver.ya_en_empresa");
            case NO_ERES_PARTE -> throw new IllegalStateException("NO_ERES_PARTE ya tratado");
        };
        // Aceptar es público (se celebra); el resto solo cierra el mensaje con el motivo. En ambos
        // casos se editan fuera los botones para que la pendiente no pueda resolverse dos veces.
        evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, desc))
                .setComponents().queue();
    }

    /** Confirma o cancela la disolución: {@code empresa:disolver:<accion>:<ownerId>}. */
    private void disolver(ButtonInteractionEvent evento, Locale locale, String id) {
        boolean si = id.startsWith(EmpresaComando.BOTON_DISOLVER_SI);
        String[] partes = id.split(":");
        // Solo el dueño resuelve su disolución (el customId lleva su id).
        if (partes.length < 4 || evento.getUser().getIdLong() != Long.parseUnsignedLong(partes[3])) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.disolver.noestuyo"))).setEphemeral(true).queue();
            return;
        }
        if (!si) {
            evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.disolver.cancelado"))).setComponents().queue();
            return;
        }
        ResultadoDisolver r = empresa.disolver(evento.getUser().getIdLong());
        String desc = switch (r) {
            case OK -> Messages.get(locale, "empresa.disuelta");
            // Cambió entre la confirmación y el clic (ya se disolvió, o dejó de ser dueño).
            case SIN_EMPRESA -> Messages.get(locale, "empresa.disolver.sin_empresa");
            case NO_ERES_DUENO -> Messages.get(locale, "empresa.disolver.no_eres_dueno");
        };
        evento.editMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, desc))
                .setComponents().queue();
    }
}
