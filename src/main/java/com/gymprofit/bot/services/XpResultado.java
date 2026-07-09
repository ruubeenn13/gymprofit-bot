package com.gymprofit.bot.services;

import com.gymprofit.bot.db.UsuarioDiscord;

/**
 * Resultado de otorgar XP a un usuario: el estado ya actualizado y si ha subido de nivel.
 *
 * @param usuario       usuario tras sumar la XP (ya persistido)
 * @param subioNivel    {@code true} si el nivel nuevo es mayor que el anterior
 * @param nivelAnterior nivel antes de sumar la XP
 * @param nivelNuevo    nivel después de sumar la XP
 */
public record XpResultado(
        UsuarioDiscord usuario,
        boolean subioNivel,
        int nivelAnterior,
        int nivelNuevo
) {
}
