package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.Optional;

/**
 * Lógica de {@code /encantar} (COMBAT-4c): mejora el arma equipada por dos vías, cobrando coins como
 * sumidero. Requiere tener un arma equipada. <b>Subir nivel</b> añade daño y su coste crece con el
 * nivel; <b>aplicar un encantamiento</b> fija un efecto elemental (catálogo {@link Encantamiento}).
 * Ambos afectan solo al combate (los lee {@link BatallaService} al iniciar la pelea).
 */
public final class EncantarService {

    /** Nivel máximo de mejora del arma. */
    public static final int NIVEL_MAX = 10;
    /** Coste base de subir un nivel (crece linealmente con el nivel actual). */
    public static final long COSTE_NIVEL_BASE = 500;

    /** Estado de una operación de encantar. */
    public enum Estado { OK, SIN_ARMA, NIVEL_MAXIMO, ENCANTO_NO_EXISTE, SIN_SALDO }

    /** Resultado de subir nivel: estado + nivel resultante + coste cobrado. */
    public record ResultadoNivel(Estado estado, int nivelNuevo, long coste) {
    }

    /** Resultado de aplicar un encantamiento: estado + id aplicado + coste cobrado. */
    public record ResultadoEncanto(Estado estado, String encantoId, long coste) {
    }

    private final PersonajeRepositorio personajes;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;

    public EncantarService(PersonajeRepositorio personajes, EconomiaRepositorio economia,
                           UsuarioDiscordRepositorio usuarios) {
        this.personajes = personajes;
        this.economia = economia;
        this.usuarios = usuarios;
    }

    /** Coste de subir del nivel actual al siguiente. */
    public static long costeNivel(int nivelActual) {
        return COSTE_NIVEL_BASE * (nivelActual + 1L);
    }

    /** Sube en 1 el nivel de mejora del arma equipada (cobra el coste creciente). */
    public ResultadoNivel subirNivel(long discordId) {
        usuarios.obtenerOCrear(discordId);
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.arma() == null) {
            return new ResultadoNivel(Estado.SIN_ARMA, 0, 0);
        }
        if (p.armaNivel() >= NIVEL_MAX) {
            return new ResultadoNivel(Estado.NIVEL_MAXIMO, p.armaNivel(), 0);
        }
        long coste = costeNivel(p.armaNivel());
        if (!economia.gastar(discordId, coste, "encantar:nivel")) {
            return new ResultadoNivel(Estado.SIN_SALDO, p.armaNivel(), coste);
        }
        personajes.subirNivelArma(discordId);
        return new ResultadoNivel(Estado.OK, p.armaNivel() + 1, coste);
    }

    /** Aplica un encantamiento del catálogo al arma equipada (cobra su precio). */
    public ResultadoEncanto aplicarEncanto(long discordId, String encantoId) {
        usuarios.obtenerOCrear(discordId);
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.arma() == null) {
            return new ResultadoEncanto(Estado.SIN_ARMA, null, 0);
        }
        Optional<Encantamiento> enc = Encantamiento.porId(encantoId);
        if (enc.isEmpty()) {
            return new ResultadoEncanto(Estado.ENCANTO_NO_EXISTE, null, 0);
        }
        long coste = enc.get().precio();
        if (!economia.gastar(discordId, coste, "encantar:" + encantoId)) {
            return new ResultadoEncanto(Estado.SIN_SALDO, encantoId, coste);
        }
        personajes.fijarEncanto(discordId, encantoId);
        return new ResultadoEncanto(Estado.OK, encantoId, coste);
    }
}
