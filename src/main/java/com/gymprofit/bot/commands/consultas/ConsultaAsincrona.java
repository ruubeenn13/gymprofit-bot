package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Punto único de manejo de errores del módulo de consultas ({@code /ejercicios},
 * {@code /ejercicio-dia} y su paginador). Todo trabajo que toque la API o la BD desde una
 * interacción ya diferida pasa por {@link #ejecutar}: se lanza en el executor propio (nunca en el
 * hilo del gateway) y <b>ningún</b> fallo puede quedarse sin respuesta ni sin traza.
 *
 * <p>Antes cada comando hacía su propio {@code runAsync} capturando solo {@link ApiException}: el
 * futuro se descartaba, así que cualquier otra {@code RuntimeException} (JDBC de las frases, la
 * carrera perdida de {@code EjercicioDiaService}, un nombre nulo del catálogo…) completaba el
 * futuro en excepción <i>en silencio</i> y el «pensando…» público del usuario se quedaba colgado
 * para siempre. Aquí se capturan las dos ramas: API caída (aviso amable) y fallo inesperado
 * (log a nivel error + aviso genérico, igual que {@code RouterComandos}).</p>
 */
public final class ConsultaAsincrona {

    private static final Logger log = LoggerFactory.getLogger(ConsultaAsincrona.class);

    /** Aviso amable de «la API se está despertando» (Render tarda ~50 s en frío). */
    private static final String CLAVE_ERROR_API = "ejercicios.error";
    /** Mismo mensaje genérico que usa el router para cualquier fallo no previsto. */
    private static final String CLAVE_ERROR_GENERICO = "comando.error.generico";

    private ConsultaAsincrona() {
    }

    /**
     * Forma del aviso de error, que no es la misma en un comando y en un componente.
     */
    public enum Aviso {
        /**
         * Comandos: el «pensando…» es un mensaje público vacío que hay que retirar antes de
         * mandar el aviso efímero (regla 13: nada de ruido público por un fallo).
         */
        BORRAR_ORIGINAL,
        /**
         * Componentes: el mensaje original es la lista/ficha que el usuario estaba mirando; se
         * deja intacta y el aviso va solo en el followup efímero.
         */
        MANTENER_ORIGINAL
    }

    /**
     * Lanza {@code trabajo} en {@code executor} y garantiza respuesta pase lo que pase.
     *
     * @param evento     interacción <b>ya diferida</b> (deferReply/deferEdit): se responde por hook
     * @param locale     idioma del usuario
     * @param executor   pool propio del bot; jamás el hilo del gateway
     * @param tipo       tipo de embed del aviso (marca el color/emoji de la categoría)
     * @param aviso      si hay que borrar el «pensando…» antes del aviso
     * @param etiqueta   identificador para los logs (p. ej. {@code "/ejercicios"})
     * @param trabajo    la consulta en sí; puede lanzar lo que quiera, aquí se contiene todo
     */
    public static void ejecutar(IDeferrableCallback evento, Locale locale, ExecutorService executor,
                                EmbedFactory.Tipo tipo, Aviso aviso, String etiqueta,
                                Runnable trabajo) {
        CompletableFuture.runAsync(() -> {
            try {
                trabajo.run();
            } catch (ApiException e) {
                // Caso esperado y frecuente: la API de Render duerme. Warn, no error.
                log.warn("La API no respondió en {}", etiqueta, e);
                responder(evento, locale, tipo, aviso, Messages.get(locale, CLAVE_ERROR_API),
                        etiqueta);
            } catch (RuntimeException e) {
                // Todo lo demás: se registra a nivel error (es un defecto, no una caída externa)
                // y el usuario recibe el mismo aviso genérico que en el resto del bot.
                log.error("Fallo inesperado en {}", etiqueta, e);
                responder(evento, locale, tipo, aviso, Messages.get(locale, CLAVE_ERROR_GENERICO),
                        etiqueta);
            }
        }, executor);
    }

    /** Manda el aviso efímero por el hook, retirando antes el «pensando…» si toca. */
    private static void responder(IDeferrableCallback evento, Locale locale, EmbedFactory.Tipo tipo,
                                  Aviso aviso, String mensaje, String etiqueta) {
        if (aviso == Aviso.BORRAR_ORIGINAL) {
            evento.getHook().deleteOriginal().queue(null, fallo(etiqueta));
        }
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(tipo, locale, mensaje))
                .setEphemeral(true).queue(null, fallo(etiqueta));
    }

    /**
     * Consumidor de fallo para los {@code queue()} de este módulo. Una consulta lenta puede
     * rebasar los 15 min que Discord da para editar una interacción diferida; cuando eso pasa la
     * API devuelve {@code 10015 Unknown Webhook} y sin este consumidor JDA escupe un stack trace
     * en el log por algo que no es un defecto. Con él es una línea informativa.
     *
     * @param etiqueta identificador para los logs
     * @return el consumidor listo para {@code .queue(null, ...)}
     */
    public static Consumer<Throwable> fallo(String etiqueta) {
        return error -> log.info("No se pudo responder a {} (interacción caducada?): {}",
                etiqueta, error.toString());
    }
}
