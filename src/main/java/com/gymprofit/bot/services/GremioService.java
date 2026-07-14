package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Gremio;
import com.gymprofit.bot.db.GremioRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.List;
import java.util.Optional;

/**
 * Lógica de gremios (F-ECO-5a). Un jugador crea un gremio (con coste, sumidero) y pasa a ser su
 * dueño; puede añadir/expulsar miembros y disolverlo. Cada jugador pertenece a lo sumo a un gremio.
 * La gestión del <b>canal privado</b> (crear/permisos/borrar) la hacen los comandos con JDA usando el
 * {@code canalId} que guarda el gremio; aquí va solo la lógica de datos.
 */
public final class GremioService {

    /** Coste de fundar un gremio (sumidero). */
    public static final long COSTE_CREAR = 5000;
    /** Máximo de miembros por gremio. */
    public static final int MAX_MIEMBROS = 10;
    public static final int NOMBRE_MIN = 3;
    public static final int NOMBRE_MAX = 32;

    public enum EstadoCrear { OK, YA_EN_GREMIO, NOMBRE_INVALIDO, NOMBRE_USADO, SIN_SALDO }

    public enum EstadoMiembro {
        OK, NO_ERES_DUENO, OBJETIVO_EN_GREMIO, LLENO, NO_TIENES, ERES_DUENO, NO_MIEMBRO, NO_PUEDES
    }

    /** Resultado de crear (id del gremio si {@code OK}). */
    public record ResultadoCrear(EstadoCrear estado, long gremioId) {
    }

    /** Resultado de una operación de miembro; incluye el gremio (para la gestión del canal). */
    public record ResultadoMiembro(EstadoMiembro estado, Gremio gremio) {
    }

    /** Información de un gremio para mostrar. */
    public record Info(Gremio gremio, List<Long> miembros) {
    }

    private final GremioRepositorio repo;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;

    public GremioService(GremioRepositorio repo, EconomiaRepositorio economia,
                         UsuarioDiscordRepositorio usuarios) {
        this.repo = repo;
        this.economia = economia;
        this.usuarios = usuarios;
    }

    /** Funda un gremio y mete al dueño como primer miembro (cobra el coste). */
    public ResultadoCrear crear(long dueno, String nombre) {
        usuarios.obtenerOCrear(dueno);
        if (repo.gremioDe(dueno).isPresent()) {
            return new ResultadoCrear(EstadoCrear.YA_EN_GREMIO, 0);
        }
        String n = nombre.trim();
        if (n.length() < NOMBRE_MIN || n.length() > NOMBRE_MAX) {
            return new ResultadoCrear(EstadoCrear.NOMBRE_INVALIDO, 0);
        }
        if (repo.existeNombre(n)) {
            return new ResultadoCrear(EstadoCrear.NOMBRE_USADO, 0);
        }
        if (!economia.gastar(dueno, COSTE_CREAR, "crear_gremio")) {
            return new ResultadoCrear(EstadoCrear.SIN_SALDO, 0);
        }
        long id = repo.crear(n, dueno);
        repo.anadirMiembro(id, dueno);
        return new ResultadoCrear(EstadoCrear.OK, id);
    }

    /** El dueño añade a un jugador a su gremio. */
    public ResultadoMiembro anadir(long dueno, long objetivo) {
        Optional<Gremio> g = repo.gremioDe(dueno);
        if (g.isEmpty() || g.get().dueno() != dueno) {
            return new ResultadoMiembro(EstadoMiembro.NO_ERES_DUENO, null);
        }
        usuarios.obtenerOCrear(objetivo);
        if (repo.gremioDe(objetivo).isPresent()) {
            return new ResultadoMiembro(EstadoMiembro.OBJETIVO_EN_GREMIO, g.get());
        }
        if (repo.contarMiembros(g.get().id()) >= MAX_MIEMBROS) {
            return new ResultadoMiembro(EstadoMiembro.LLENO, g.get());
        }
        repo.anadirMiembro(g.get().id(), objetivo);
        return new ResultadoMiembro(EstadoMiembro.OK, g.get());
    }

    /** Un miembro (no dueño) abandona su gremio. */
    public ResultadoMiembro salir(long discordId) {
        Optional<Gremio> g = repo.gremioDe(discordId);
        if (g.isEmpty()) {
            return new ResultadoMiembro(EstadoMiembro.NO_TIENES, null);
        }
        if (g.get().dueno() == discordId) {
            return new ResultadoMiembro(EstadoMiembro.ERES_DUENO, g.get());
        }
        repo.quitarMiembro(discordId);
        return new ResultadoMiembro(EstadoMiembro.OK, g.get());
    }

    /** El dueño expulsa a un miembro. */
    public ResultadoMiembro expulsar(long dueno, long objetivo) {
        Optional<Gremio> g = repo.gremioDe(dueno);
        if (g.isEmpty() || g.get().dueno() != dueno) {
            return new ResultadoMiembro(EstadoMiembro.NO_ERES_DUENO, null);
        }
        if (objetivo == dueno) {
            return new ResultadoMiembro(EstadoMiembro.NO_PUEDES, g.get());
        }
        Optional<Gremio> go = repo.gremioDe(objetivo);
        if (go.isEmpty() || go.get().id() != g.get().id()) {
            return new ResultadoMiembro(EstadoMiembro.NO_MIEMBRO, g.get());
        }
        repo.quitarMiembro(objetivo);
        return new ResultadoMiembro(EstadoMiembro.OK, g.get());
    }

    /** El dueño disuelve el gremio (devuelve el gremio para borrar su canal). */
    public ResultadoMiembro disolver(long dueno) {
        Optional<Gremio> g = repo.gremioDe(dueno);
        if (g.isEmpty()) {
            return new ResultadoMiembro(EstadoMiembro.NO_TIENES, null);
        }
        if (g.get().dueno() != dueno) {
            return new ResultadoMiembro(EstadoMiembro.NO_ERES_DUENO, null);
        }
        repo.eliminar(g.get().id());
        return new ResultadoMiembro(EstadoMiembro.OK, g.get());
    }

    /** Información del gremio de un jugador (para {@code /gremio}). */
    public Optional<Info> info(long discordId) {
        return repo.gremioDe(discordId).map(g -> new Info(g, repo.miembros(g.id())));
    }

    /** Guarda el id del canal creado para el gremio. */
    public void fijarCanal(long gremioId, long canalId) {
        repo.fijarCanal(gremioId, canalId);
    }
}
