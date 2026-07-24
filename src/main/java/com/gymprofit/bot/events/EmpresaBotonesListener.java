package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.economia.EmpresaCanal;
import com.gymprofit.bot.commands.economia.EmpresaComando;
import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaPropuestaRepositorio;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.Pendiente;
import com.gymprofit.bot.db.Propuesta;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EmpresaGestionService;
import com.gymprofit.bot.services.EmpresaGestionService.ResultadoVoto;
import com.gymprofit.bot.services.EmpresaService;
import com.gymprofit.bot.services.EmpresaService.ResultadoDisolver;
import com.gymprofit.bot.services.EmpresaService.ResultadoResolver;
import com.gymprofit.bot.services.TipoPropuesta;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Locale;
import java.util.Optional;

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
    /** Lecturas de apoyo para la sincronización del canal (F4): pendiente del ingreso y empresa por id. */
    private final EmpresaRepositorio repo;
    /** Lectura de la propuesta antes de votar (F4): su aprobación la cierra y luego no se podría leer. */
    private final EmpresaPropuestaRepositorio propuestasRepo;

    public EmpresaBotonesListener(EmpresaService empresa, EmpresaGestionService gestion,
                                  EmpresaRepositorio repo, EmpresaPropuestaRepositorio propuestasRepo) {
        this.empresa = empresa;
        this.gestion = gestion;
        this.repo = repo;
        this.propuestasRepo = propuestasRepo;
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
        // F4: el tipo y el objetivo de la propuesta se leen ANTES de votar, porque una aprobación la cierra
        // (borra) y después ya no se podrían recuperar. Solo se usan si la propuesta era SACAR/DESPEDIR.
        Optional<Propuesta> propAntes = propuestasRepo.porId(propuestaId);
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
        // F4: si el voto aprobó y ejecutó una propuesta de SACAR/DESPEDIR, se quita al objetivo del canal
        // (cambiar rango/ascenso NO tocan la pertenencia, así que no aplican). Best-effort. El canalId sale
        // de la empresa de la propuesta (leída antes de votar); la empresa sigue existiendo.
        if (r == ResultadoVoto.APROBADA_EJECUTADA && evento.getGuild() != null) {
            propAntes.filter(p -> p.tipo() == TipoPropuesta.SACAR || p.tipo() == TipoPropuesta.DESPEDIR)
                    .ifPresent(p -> repo.porId(p.empresaId()).map(Empresa::canalId).ifPresent(canalId ->
                            EmpresaCanal.quitar(evento.getGuild(), canalId, p.objetivoId())));
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
        // F4: el nuevo miembro es el titular de la pendiente (el invitado en una INVITACION, el solicitante
        // en una SOLICITUD; nunca el dueño que aprueba). Se capta ANTES de resolver porque resolver borra
        // la pendiente y después no se sabría a quién dar acceso al canal.
        Long nuevoMiembroId = repo.pendiente(pendienteId).map(Pendiente::discordId).orElse(null);
        ResultadoResolver r = empresa.resolver(pendienteId, aceptar, evento.getUser().getIdLong());
        // NO_ERES_PARTE no toca el mensaje (otro podría resolverlo aún): se avisa efímero y se sale.
        if (r == ResultadoResolver.NO_ERES_PARTE) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.resolver.no_eres_parte"))).setEphemeral(true).queue();
            return;
        }
        // F4: al aceptar, el nuevo miembro entra → se materializa el canal si aún no existe (empresa vieja
        // o fundación sin canal) y se le da acceso. Best-effort.
        if (r == ResultadoResolver.ACEPTADO && evento.getGuild() != null && nuevoMiembroId != null) {
            long miembroId = nuevoMiembroId;
            empresa.infoDe(miembroId).ifPresent(info -> {
                Empresa emp = info.empresa();
                if (emp.canalId() == null) {
                    EmpresaCanal.materializar(evento.getGuild(), emp, repo);
                } else {
                    EmpresaCanal.anadir(evento.getGuild(), emp.canalId(), miembroId);
                }
            });
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
        // F4: el canalId se capta ANTES de disolver, porque disolver borra la empresa (y su fila) en
        // cascada y después no se podría leer para eliminar el canal.
        Long canalId = empresa.infoDe(evento.getUser().getIdLong())
                .map(i -> i.empresa().canalId()).orElse(null);
        ResultadoDisolver r = empresa.disolver(evento.getUser().getIdLong());
        // Tras una disolución real se borra el canal privado. Best-effort.
        if (r == ResultadoDisolver.OK && evento.getGuild() != null && canalId != null) {
            EmpresaCanal.eliminar(evento.getGuild(), canalId);
        }
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
