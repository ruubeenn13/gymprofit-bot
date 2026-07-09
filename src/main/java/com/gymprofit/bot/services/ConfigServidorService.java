package com.gymprofit.bot.services;

import com.gymprofit.bot.db.ConfigServidor;
import com.gymprofit.bot.db.ConfigServidorRepositorio;

/**
 * Lógica de configuración por servidor: leer la config y fijar un canal, un rol de objetivo o el
 * idioma de forma puntual (cada operación conserva el resto de la configuración).
 */
public final class ConfigServidorService {

    /** Canales configurables del servidor. */
    public enum TipoCanal {
        BIENVENIDA, EJERCICIO_DIA, LOGROS, SUGERENCIAS, SOPORTE, BOT_LOGS
    }

    /** Objetivos de entrenamiento con rol asociado (auto-roles de la bienvenida). */
    public enum Objetivo {
        FUERZA, CARDIO, PERDIDA_PESO, GENERAL
    }

    private final ConfigServidorRepositorio repositorio;

    public ConfigServidorService(ConfigServidorRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /** Configuración del servidor (creándola por defecto si no existía). */
    public ConfigServidor obtener(long guildId) {
        return repositorio.obtenerOCrear(guildId);
    }

    /** Fija (o limpia, con {@code null}) el canal del tipo indicado. */
    public ConfigServidor fijarCanal(long guildId, TipoCanal tipo, Long canalId) {
        ConfigServidor a = repositorio.obtenerOCrear(guildId);
        ConfigServidor actualizado = switch (tipo) {
            case BIENVENIDA -> new ConfigServidor(a.guildId(), a.idioma(), canalId,
                    a.canalEjercicioDia(), a.canalLogros(), a.canalSugerencias(), a.canalSoporte(),
                    a.canalBotLogs(), a.rolObjetivoFuerza(), a.rolObjetivoCardio(),
                    a.rolObjetivoPerdidaPeso(), a.rolObjetivoGeneral());
            case EJERCICIO_DIA -> new ConfigServidor(a.guildId(), a.idioma(), a.canalBienvenida(),
                    canalId, a.canalLogros(), a.canalSugerencias(), a.canalSoporte(),
                    a.canalBotLogs(), a.rolObjetivoFuerza(), a.rolObjetivoCardio(),
                    a.rolObjetivoPerdidaPeso(), a.rolObjetivoGeneral());
            case LOGROS -> new ConfigServidor(a.guildId(), a.idioma(), a.canalBienvenida(),
                    a.canalEjercicioDia(), canalId, a.canalSugerencias(), a.canalSoporte(),
                    a.canalBotLogs(), a.rolObjetivoFuerza(), a.rolObjetivoCardio(),
                    a.rolObjetivoPerdidaPeso(), a.rolObjetivoGeneral());
            case SUGERENCIAS -> new ConfigServidor(a.guildId(), a.idioma(), a.canalBienvenida(),
                    a.canalEjercicioDia(), a.canalLogros(), canalId, a.canalSoporte(),
                    a.canalBotLogs(), a.rolObjetivoFuerza(), a.rolObjetivoCardio(),
                    a.rolObjetivoPerdidaPeso(), a.rolObjetivoGeneral());
            case SOPORTE -> new ConfigServidor(a.guildId(), a.idioma(), a.canalBienvenida(),
                    a.canalEjercicioDia(), a.canalLogros(), a.canalSugerencias(), canalId,
                    a.canalBotLogs(), a.rolObjetivoFuerza(), a.rolObjetivoCardio(),
                    a.rolObjetivoPerdidaPeso(), a.rolObjetivoGeneral());
            case BOT_LOGS -> new ConfigServidor(a.guildId(), a.idioma(), a.canalBienvenida(),
                    a.canalEjercicioDia(), a.canalLogros(), a.canalSugerencias(), a.canalSoporte(),
                    canalId, a.rolObjetivoFuerza(), a.rolObjetivoCardio(),
                    a.rolObjetivoPerdidaPeso(), a.rolObjetivoGeneral());
        };
        repositorio.guardar(actualizado);
        return actualizado;
    }

    /** Fija (o limpia, con {@code null}) el rol asociado a un objetivo. */
    public ConfigServidor fijarRol(long guildId, Objetivo objetivo, Long rolId) {
        ConfigServidor a = repositorio.obtenerOCrear(guildId);
        ConfigServidor actualizado = switch (objetivo) {
            case FUERZA -> new ConfigServidor(a.guildId(), a.idioma(), a.canalBienvenida(),
                    a.canalEjercicioDia(), a.canalLogros(), a.canalSugerencias(), a.canalSoporte(),
                    a.canalBotLogs(), rolId, a.rolObjetivoCardio(),
                    a.rolObjetivoPerdidaPeso(), a.rolObjetivoGeneral());
            case CARDIO -> new ConfigServidor(a.guildId(), a.idioma(), a.canalBienvenida(),
                    a.canalEjercicioDia(), a.canalLogros(), a.canalSugerencias(), a.canalSoporte(),
                    a.canalBotLogs(), a.rolObjetivoFuerza(), rolId,
                    a.rolObjetivoPerdidaPeso(), a.rolObjetivoGeneral());
            case PERDIDA_PESO -> new ConfigServidor(a.guildId(), a.idioma(), a.canalBienvenida(),
                    a.canalEjercicioDia(), a.canalLogros(), a.canalSugerencias(), a.canalSoporte(),
                    a.canalBotLogs(), a.rolObjetivoFuerza(), a.rolObjetivoCardio(),
                    rolId, a.rolObjetivoGeneral());
            case GENERAL -> new ConfigServidor(a.guildId(), a.idioma(), a.canalBienvenida(),
                    a.canalEjercicioDia(), a.canalLogros(), a.canalSugerencias(), a.canalSoporte(),
                    a.canalBotLogs(), a.rolObjetivoFuerza(), a.rolObjetivoCardio(),
                    a.rolObjetivoPerdidaPeso(), rolId);
        };
        repositorio.guardar(actualizado);
        return actualizado;
    }

    /** Fija el idioma por defecto del servidor. */
    public ConfigServidor fijarIdioma(long guildId, String idioma) {
        ConfigServidor a = repositorio.obtenerOCrear(guildId);
        ConfigServidor actualizado = new ConfigServidor(a.guildId(), idioma, a.canalBienvenida(),
                a.canalEjercicioDia(), a.canalLogros(), a.canalSugerencias(), a.canalSoporte(),
                a.canalBotLogs(), a.rolObjetivoFuerza(), a.rolObjetivoCardio(),
                a.rolObjetivoPerdidaPeso(), a.rolObjetivoGeneral());
        repositorio.guardar(actualizado);
        return actualizado;
    }
}
