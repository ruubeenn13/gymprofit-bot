package com.gymprofit.bot.db;

import java.time.LocalDate;

/**
 * Estado de gamificación de un usuario de Discord (tabla {@code usuarios_discord}).
 *
 * @param discordId        ID de usuario de Discord (snowflake)
 * @param xp               puntos de experiencia acumulados
 * @param nivel            nivel derivado de la curva de XP
 * @param coins            moneda interna del bot
 * @param racha            días consecutivos de actividad
 * @param ultimaRachaFecha último día que sumó racha ({@code null} si nunca)
 * @param idioma           override de idioma del usuario ({@code es}/{@code en})
 * @param optOutLogros     si el usuario NO quiere publicar sus logros
 */
public record UsuarioDiscord(
        long discordId,
        int xp,
        int nivel,
        int coins,
        int racha,
        LocalDate ultimaRachaFecha,
        String idioma,
        boolean optOutLogros
) {
}
