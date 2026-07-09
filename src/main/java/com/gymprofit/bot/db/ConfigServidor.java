package com.gymprofit.bot.db;

/**
 * Configuración de un servidor (tabla {@code config_servidor}). Los canales y roles se guardan
 * como IDs de Discord (snowflakes); {@code null} = aún sin configurar por el staff.
 *
 * @param guildId                 ID del servidor de Discord
 * @param idioma                  idioma por defecto del servidor ({@code es}/{@code en})
 * @param canalBienvenida         canal donde se publica la bienvenida
 * @param canalEjercicioDia       canal del ejercicio del día
 * @param canalLogros             canal donde se comparten logros
 * @param canalSugerencias        canal de sugerencias con votación
 * @param canalSoporte            canal de soporte con el botón de abrir ticket
 * @param canalBotLogs            canal privado de staff para logs del bot
 * @param rolObjetivoFuerza       rol del objetivo Fuerza
 * @param rolObjetivoCardio       rol del objetivo Cardio
 * @param rolObjetivoPerdidaPeso  rol del objetivo Pérdida de peso
 * @param rolObjetivoGeneral      rol del objetivo General
 */
public record ConfigServidor(
        long guildId,
        String idioma,
        Long canalBienvenida,
        Long canalEjercicioDia,
        Long canalLogros,
        Long canalSugerencias,
        Long canalSoporte,
        Long canalBotLogs,
        Long rolObjetivoFuerza,
        Long rolObjetivoCardio,
        Long rolObjetivoPerdidaPeso,
        Long rolObjetivoGeneral
) {

    /** Configuración por defecto de un servidor recién visto: idioma español y todo sin fijar. */
    public static ConfigServidor porDefecto(long guildId) {
        return new ConfigServidor(guildId, "es",
                null, null, null, null, null, null,
                null, null, null, null);
    }
}
