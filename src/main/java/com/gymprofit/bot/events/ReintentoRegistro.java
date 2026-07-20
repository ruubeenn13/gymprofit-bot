package com.gymprofit.bot.events;

import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acciones que se quedaron a medias porque el jugador estaba dormido (currar, minar, pelear,
 * entrar a una mazmorra), guardadas para <b>relanzarlas en cuanto despierte</b>.
 *
 * <p>Sin esto, despertarse desde el botón dejaba al jugador donde empezó: se levantaba y tenía que
 * volver a escribir el comando, que es justo la fricción que el botón venía a quitar.
 *
 * <p>En memoria y <b>una por jugador</b> (la última manda): es un puente entre dos interacciones
 * seguidas, no un estado que merezca BD — si el bot se reinicia, lo peor que pasa es que haya que
 * repetir el comando. Caduca a los {@link #TTL} para no relanzar dentro de una hora algo que se
 * pidió antes de irse a dormir.
 *
 * <p>La acción devuelve el <b>mensaje ya construido</b> ({@link MessageCreateData}) en vez de
 * enviarlo ella: así el registro no depende de ningún hook y quien lo consuma decide cómo mandarlo
 * (mensaje nuevo, edición…). Permite además pintar acciones con componentes, como la batalla.
 */
public final class ReintentoRegistro {

    /** Ventana en la que un reintento sigue teniendo sentido. */
    public static final Duration TTL = Duration.ofMinutes(30);

    /** Acción bloqueada por el sueño. Se ejecuta al despertar y devuelve qué mandar. */
    @FunctionalInterface
    public interface Accion {
        MessageCreateData ejecutar(Locale locale);
    }

    private record Entrada(Accion accion, Instant guardadaEn) {
    }

    private final Map<Long, Entrada> pendientes = new ConcurrentHashMap<>();

    /** Guarda (o reemplaza) la acción pendiente del jugador. */
    public void guardar(long discordId, Instant ahora, Accion accion) {
        pendientes.put(discordId, new Entrada(accion, ahora));
    }

    /**
     * Saca la acción pendiente y la <b>borra</b>: un reintento se consume una sola vez, aunque el
     * jugador vuelva a pulsar el botón.
     *
     * @return la acción, o vacío si no había o si ya caducó
     */
    public Optional<Accion> tomar(long discordId, Instant ahora) {
        Entrada e = pendientes.remove(discordId);
        if (e == null || Duration.between(e.guardadaEn(), ahora).compareTo(TTL) > 0) {
            return Optional.empty();
        }
        return Optional.of(e.accion());
    }

    /** Descarta lo pendiente sin ejecutarlo (el jugador ha decidido seguir durmiendo). */
    public void descartar(long discordId) {
        pendientes.remove(discordId);
    }
}
