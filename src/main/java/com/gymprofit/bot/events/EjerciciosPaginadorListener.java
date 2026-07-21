package com.gymprofit.bot.events;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.api.dtos.PaginaDTO;
import com.gymprofit.bot.commands.consultas.EjerciciosComando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EjercicioService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Navegación de {@code /ejercicios}: flechas ◀ ▶ (recalcula la página), menú desplegable
 * (pasa el mismo mensaje a la ficha) y botón «Volver a la lista». Todo el estado viene en el
 * {@code customId} (ver {@link EjerciciosComando.Filtros}): sobrevive a reinicios del bot.
 * Solo el dueño de la búsqueda puede usar sus controles; las llamadas a la API van al executor
 * propio con {@code deferEdit()} previo (la API puede tardar en despertar).
 */
public final class EjerciciosPaginadorListener extends ListenerAdapter {

    private final EjercicioService ejercicios;
    private final ExecutorService executor;

    public EjerciciosPaginadorListener(EjercicioService ejercicios, ExecutorService executor) {
        this.ejercicios = ejercicios;
        this.executor = executor;
    }

    /**
     * Flechas ◀ ▶ y botón «Volver»: ambos re-pintan la lista en la página que trae el estado
     * (el botón de volver codifica la página de la que se salió a la ficha).
     */
    @Override
    public void onButtonInteraction(ButtonInteractionEvent evento) {
        String id = evento.getComponentId();
        // Los tres prefijos no se prefijan entre sí ("ejercicios-volver:" no empieza por
        // "ejercicios:"), así que basta con probar el más específico primero.
        String prefijo = id.startsWith(EjerciciosComando.PREFIJO_VOLVER)
                ? EjerciciosComando.PREFIJO_VOLVER
                : id.startsWith(EjerciciosComando.PREFIJO_NAV) ? EjerciciosComando.PREFIJO_NAV : null;
        if (prefijo == null) {
            return; // botón de otro comando
        }
        var filtros = EjerciciosComando.Filtros.parsear(id, prefijo);
        // El aviso de «no es tuyo» es una respuesta propia: comprobar ANTES de deferEdit(),
        // porque a una interacción solo se le puede responder una vez.
        if (!esDueno(evento, filtros.ownerId())) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferEdit().queue();
        CompletableFuture.runAsync(() -> {
            try {
                PaginaDTO<EjercicioDTO> pagina = ejercicios.buscar(filtros.busqueda(),
                        filtros.grupo(), filtros.dificultad(), filtros.pagina(),
                        locale.getLanguage());
                evento.getHook().editOriginalEmbeds(
                                EjerciciosComando.construirLista(locale, pagina, filtros))
                        .setComponents(EjerciciosComando.construirComponentes(locale, pagina, filtros))
                        .queue();
            } catch (ApiException e) {
                avisarError(evento, locale);
            }
        }, executor);
    }

    /** Menú desplegable: convierte el mismo mensaje en la ficha del ejercicio elegido. */
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent evento) {
        String id = evento.getComponentId();
        if (!id.startsWith(EjerciciosComando.PREFIJO_SEL)) {
            return;
        }
        var filtros = EjerciciosComando.Filtros.parsear(id, EjerciciosComando.PREFIJO_SEL);
        if (!esDueno(evento, filtros.ownerId())) {
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        // El value de la opción es el id del ejercicio (lo puso construirComponentes).
        int ejercicioId = Integer.parseInt(evento.getValues().get(0));
        evento.deferEdit().queue();
        CompletableFuture.runAsync(() -> {
            try {
                EjercicioDTO ficha = ejercicios.porId(ejercicioId, locale.getLanguage());
                evento.getHook().editOriginalEmbeds(
                                EjerciciosComando.construirFicha(locale, ficha))
                        .setComponents(EjerciciosComando.construirBotonVolver(locale, filtros))
                        .queue();
            } catch (ApiException e) {
                avisarError(evento, locale);
            }
        }, executor);
    }

    /** Solo el dueño usa sus controles (como en el resto del bot); el aviso va efímero. */
    private static boolean esDueno(GenericComponentInteractionCreateEvent evento, long ownerId) {
        if (evento.getUser().getIdLong() == ownerId) {
            return true;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "ejercicios.noestuyo"))).setEphemeral(true).queue();
        return false;
    }

    /** El mensaje original se queda como está; el error va en followup efímero (regla 13). */
    private static void avisarError(GenericComponentInteractionCreateEvent evento, Locale locale) {
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "ejercicios.error"))).setEphemeral(true).queue();
    }
}
