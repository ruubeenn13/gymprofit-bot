package com.gymprofit.bot.api;

/**
 * Error hablando con la API GymProFit (red caída, 5xx persistente, respuesta inválida). Es
 * unchecked para que los comandos la capturen en un único punto y respondan con un aviso amable
 * (nunca una traza al usuario).
 */
public class ApiException extends RuntimeException {
    public ApiException(String mensaje) { super(mensaje); }
    public ApiException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
