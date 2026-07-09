package com.gymprofit.bot.services;

import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

/**
 * Lógica de experiencia y niveles. Otorga XP a un usuario, recalcula su nivel con
 * {@link NivelCalculadora} y persiste el resultado. La política de cuándo y cuánta XP dar (p. ej.
 * el cooldown por mensaje) vive en el listener que llama a este service, no aquí.
 */
public final class XpService {

    private final UsuarioDiscordRepositorio repositorio;

    public XpService(UsuarioDiscordRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Suma XP a un usuario (creándolo si no existía), recalcula el nivel y guarda.
     *
     * @param discordId ID del usuario de Discord
     * @param cantidad  XP a sumar ({@code > 0})
     * @return el resultado, incluyendo si ha subido de nivel
     */
    public XpResultado ganarXp(long discordId, int cantidad) {
        UsuarioDiscord actual = repositorio.obtenerOCrear(discordId);

        int xpNueva = actual.xp() + cantidad;
        int nivelAnterior = NivelCalculadora.nivelDeXp(actual.xp());
        int nivelNuevo = NivelCalculadora.nivelDeXp(xpNueva);

        UsuarioDiscord actualizado = new UsuarioDiscord(
                actual.discordId(), xpNueva, nivelNuevo, actual.coins(), actual.racha(),
                actual.ultimaRachaFecha(), actual.idioma(), actual.optOutLogros());
        repositorio.guardar(actualizado);

        return new XpResultado(actualizado, nivelNuevo > nivelAnterior, nivelAnterior, nivelNuevo);
    }
}
