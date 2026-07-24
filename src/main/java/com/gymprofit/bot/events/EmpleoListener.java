package com.gymprofit.bot.events;

import com.gymprofit.bot.commands.economia.EmpleoComando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EmpresaService;
import com.gymprofit.bot.services.EmpresaService.ResultadoIngreso;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.util.Locale;

/**
 * Bolsa de empleo (F5c): el botón <i>Solicitar</i> del tablón {@code /empleo} abre un modal que pide el
 * motivo; al enviarlo se crea la SOLICITUD por id ({@link EmpresaService#solicitarPorId}, misma validación
 * que {@code /empresa solicitar}) que resolverá el dueño por el flujo de pendientes de F1. Primer uso de
 * modales del bot.
 *
 * <p>El botón y el modal comparten customId ({@code empleo:solicitar:<empresaId>}): así el id de la empresa
 * viaja del clic al envío sin estado en memoria. Toda la potestad y las reglas viven en el service; aquí solo
 * se abre el modal, se llama a {@code solicitarPorId} y se traduce el resultado (reusando las claves i18n de
 * error de {@link ResultadoIngreso} que ya usa {@code /empresa solicitar}, no se inventan nuevas).</p>
 */
public final class EmpleoListener extends ListenerAdapter {

    /** Prefijo común del customId del botón y del modal: {@code empleo:solicitar:}. */
    private static final String PREFIJO = EmpleoComando.BOTON_SOLICITAR + ":";
    /** Id del campo de texto del modal donde se escribe el motivo (opcional). */
    private static final String INPUT_MOTIVO = "motivo";

    private final EmpresaService empresa;

    public EmpleoListener(EmpresaService empresa) {
        this.empresa = empresa;
    }

    /**
     * Botón <i>Solicitar</i> ({@code empleo:solicitar:<empresaId>}): abre un modal con un único campo de
     * motivo (opcional, máx. 300). El modal hereda el customId del botón para arrastrar el id de la empresa
     * hasta el envío. No se valida nada aquí: la validación entera es de {@code solicitarPorId}.
     */
    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        if (!id.startsWith(PREFIJO)) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long empresaId = Long.parseLong(id.substring(PREFIJO.length()));
        TextInput motivo = TextInput.create(INPUT_MOTIVO,
                        Messages.get(locale, "empleo.solicitar.modal.motivo"), TextInputStyle.PARAGRAPH)
                .setRequired(false)
                .setMaxLength(300)
                .build();
        Modal modal = Modal.create(PREFIJO + empresaId,
                        Messages.get(locale, "empleo.solicitar.modal.titulo"))
                .addComponents(ActionRow.of(motivo))
                .build();
        evento.replyModal(modal).queue();
    }

    /**
     * Envío del modal ({@code empleo:solicitar:<empresaId>}): crea la solicitud con el motivo escrito (o
     * vacío si se dejó en blanco). El resultado se traduce y se responde <b>efímero</b>: solo lo ve quien
     * solicita, la decisión pública la toma el dueño al resolver la pendiente.
     */
    @Override
    public void onModalInteraction(ModalInteractionEvent evento) {
        String id = evento.getModalId();
        if (!id.startsWith(PREFIJO)) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long empresaId = Long.parseLong(id.substring(PREFIJO.length()));
        // El motivo es opcional: si el campo va vacío, getValue puede ser null → cadena vacía.
        ModalMapping campo = evento.getValue(INPUT_MOTIVO);
        String motivo = campo == null ? "" : campo.getAsString();
        ResultadoIngreso r = empresa.solicitarPorId(evento.getUser().getIdLong(), empresaId, motivo);
        // Claves de error reusadas literalmente de EmpresaComando (empresa.ingreso.*); solo el OK y los dos
        // casos propios de la bolsa (sin_trabajo / ya_en_empresa) tienen mensajes específicos de /empleo.
        String msg = switch (r) {
            case OK -> Messages.get(locale, "empleo.solicitar.enviada");
            case SIN_TRABAJO -> Messages.get(locale, "empleo.sin_trabajo");
            case YA_EN_EMPRESA -> Messages.get(locale, "empleo.ya_en_empresa");
            case OTRA_RAMA -> Messages.get(locale, "empresa.ingreso.otra_rama");
            case EMPRESA_NO_EXISTE -> Messages.get(locale, "empresa.ingreso.empresa_no_existe");
            case YA_PENDIENTE -> Messages.get(locale, "empresa.ingreso.ya_pendiente");
            case ES_MISMO -> Messages.get(locale, "empresa.ingreso.es_mismo");
        };
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, msg))
                .setEphemeral(true).queue();
    }
}
