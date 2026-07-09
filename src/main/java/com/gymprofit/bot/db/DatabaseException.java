package com.gymprofit.bot.db;

/**
 * Error no recuperable de acceso a la BD. Envuelve las {@link java.sql.SQLException} de los
 * repositorios para no propagar excepciones comprobadas por toda la capa de dominio, sin
 * silenciarlas nunca (siempre conserva la causa original).
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
