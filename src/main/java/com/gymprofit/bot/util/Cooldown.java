package com.gymprofit.bot.util;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Control de cooldown por clave (normalmente un ID de usuario de Discord), en memoria y
 * thread-safe. Sirve como anti-spam: p. ej. limitar la XP a un mensaje cada 60&nbsp;s.
 *
 * <p>El estado es en memoria a propósito: se reinicia al reiniciar el bot, lo cual es
 * aceptable para un anti-abuso (el free tier de Render reinicia el proceso de todas formas).</p>
 */
public final class Cooldown {

    private final long ventanaMillis;
    private final Map<Long, Long> ultimoUso = new ConcurrentHashMap<>();

    /**
     * @param ventana tiempo mínimo que debe pasar entre usos permitidos de una misma clave
     */
    public Cooldown(Duration ventana) {
        this.ventanaMillis = ventana.toMillis();
    }

    /**
     * Comprueba si la clave puede actuar en {@code ahoraMillis}; si puede, registra el uso.
     * El instante se recibe como parámetro para poder testear sin depender del reloj real.
     *
     * @param clave       identificador (p. ej. ID de usuario)
     * @param ahoraMillis instante actual en milisegundos (epoch)
     * @return {@code true} si está permitido (y queda registrado), {@code false} si aún enfría
     */
    public boolean intentar(long clave, long ahoraMillis) {
        Long ultimo = ultimoUso.get(clave);
        if (ultimo != null && ahoraMillis - ultimo < ventanaMillis) {
            return false;
        }
        ultimoUso.put(clave, ahoraMillis);
        return true;
    }
}
