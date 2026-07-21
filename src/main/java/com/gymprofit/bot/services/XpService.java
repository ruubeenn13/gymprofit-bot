package com.gymprofit.bot.services;

import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

/**
 * Lógica de experiencia y niveles. Otorga XP a un usuario, recalcula su nivel con
 * {@link NivelCalculadora} y persiste el resultado. La política de cuándo y cuánta XP dar (p. ej.
 * el cooldown por mensaje) vive en el listener que llama a este service, no aquí.
 *
 * <p>Aquí entra también el bono de {@link Pasivos.Tipo#XP} de los efectos pasivos
 * ({@link PasivoService}): {@link #ganarXp} es el <b>único punto</b> por el que pasa toda la XP del
 * bot, así que ningún llamante tiene que enterarse.
 */
public final class XpService {

    private final UsuarioDiscordRepositorio repositorio;
    /** Efectos pasivos del jugador. {@code null} en los tests y arranques que no los usan. */
    private final PasivoService pasivos;

    public XpService(UsuarioDiscordRepositorio repositorio, PasivoService pasivos) {
        this.repositorio = repositorio;
        this.pasivos = pasivos;
    }

    /**
     * Constructor sin pasivos: equivale a no tener ninguno equipado. Existe para no reescribir los
     * tests de XP, que no tienen nada que ver con este módulo, y para los arranques degradados.
     */
    public XpService(UsuarioDiscordRepositorio repositorio) {
        this(repositorio, null);
    }

    /**
     * Suma XP a un usuario (creándolo si no existía), recalcula el nivel y guarda.
     *
     * <p>El bono de pasivos se aplica <b>antes</b> de recalcular el nivel: si es el bono lo que
     * cruza el umbral, la subida se detecta igual y el llamante puede anunciarla y sincronizar el
     * rol de rango como siempre.
     *
     * @param discordId ID del usuario de Discord
     * @param cantidad  XP a sumar ({@code > 0})
     * @return el resultado, incluyendo si ha subido de nivel
     */
    public XpResultado ganarXp(long discordId, int cantidad) {
        UsuarioDiscord actual = repositorio.obtenerOCrear(discordId);

        // Solo interesa un tipo de bono, así que basta un bonoDe (que por dentro es un único
        // bonosDe). Sale barato en el camino caliente —la XP por mensaje— porque quien no tiene
        // nada equipado se resuelve con una sola consulta: PasivoService corta en seco si el mapa
        // de ranuras viene vacío.
        int ganada = conBonoPasivos(cantidad,
                pasivos == null ? 0.0 : pasivos.bonoDe(discordId, Pasivos.Tipo.XP));
        int xpNueva = actual.xp() + ganada;
        int nivelAnterior = NivelCalculadora.nivelDeXp(actual.xp());
        int nivelNuevo = NivelCalculadora.nivelDeXp(xpNueva);

        UsuarioDiscord actualizado = new UsuarioDiscord(
                actual.discordId(), xpNueva, nivelNuevo, actual.coins(), actual.racha(),
                actual.ultimaRachaFecha(), actual.idioma(), actual.optOutLogros());
        repositorio.guardar(actualizado);

        return new XpResultado(actualizado, nivelNuevo > nivelAnterior, nivelAnterior, nivelNuevo);
    }

    /**
     * Aplica el bono de XP de los efectos pasivos. <b>Puro.</b>
     *
     * <p>Con <b>suelo de +1</b> cuando el bono es positivo y la base ≥ 1: que un +20 % sobre 2 XP se
     * redondeara a 2 (sin ganancia) se leería como un bug, no como un redondeo. El bono se vuelve a
     * topar aquí ({@code Pasivos.TOPES}) aunque ya venga topado del service, para que la función sea
     * segura por sí sola; y un bono negativo se ignora, nunca resta XP.
     *
     * @param base XP base a otorgar
     * @param bono bono de XP de los pasivos (fracción)
     * @return la XP a sumar de verdad
     */
    public static int conBonoPasivos(int base, double bono) {
        if (bono <= 0 || base <= 0) {
            return base;
        }
        double aplicado = Math.min(Pasivos.TOPES.get(Pasivos.Tipo.XP), bono);
        return Math.max(base + 1, (int) Math.round(base * (1 + aplicado)));
    }
}
