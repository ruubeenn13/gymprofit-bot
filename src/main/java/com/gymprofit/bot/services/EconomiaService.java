package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio.ResultadoDaily;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Servicio de economía (cimientos del RPG): saldo, recompensa diaria y perfil del personaje.
 * Garantiza que existen las filas de {@code usuarios_discord} y {@code personajes} antes de operar.
 * El límite del día se calcula en la zona de la comunidad para que el {@code /daily} sea coherente.
 */
public final class EconomiaService {

    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    /** Vista del perfil: personaje + saldo. */
    public record Perfil(Personaje personaje, long coins) {
    }

    private final EconomiaRepositorio economia;
    private final PersonajeRepositorio personajes;
    private final UsuarioDiscordRepositorio usuarios;

    public EconomiaService(EconomiaRepositorio economia, PersonajeRepositorio personajes,
                           UsuarioDiscordRepositorio usuarios) {
        this.economia = economia;
        this.personajes = personajes;
        this.usuarios = usuarios;
    }

    /** Saldo de coins del usuario (crea su perfil si es nuevo). */
    public long saldo(long discordId) {
        usuarios.obtenerOCrear(discordId);
        return economia.saldo(discordId);
    }

    /** Cobra la recompensa diaria (crea el perfil si es nuevo). */
    public ResultadoDaily daily(long discordId) {
        usuarios.obtenerOCrear(discordId);
        return economia.cobrarDaily(discordId, LocalDate.now(ZONA));
    }

    /** Perfil del personaje: atributos, energía, salud y saldo. */
    public Perfil perfil(long discordId) {
        usuarios.obtenerOCrear(discordId);
        Personaje personaje = personajes.obtenerOCrear(discordId);
        return new Perfil(personaje, economia.saldo(discordId));
    }
}
