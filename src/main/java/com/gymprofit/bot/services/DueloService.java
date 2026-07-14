package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Duelos de apuesta entre jugadores (F-ECO-6): dos jugadores apuestan lo mismo y, al azar 50/50, el
 * ganador se lleva ambas apuestas. Es de suma cero entre los dos (no infla la economía). Requiere que
 * el retado acepte (con un botón). Las ofertas vivas se guardan en memoria (se pierden si el bot
 * reinicia). El azar se inyecta para tests deterministas.
 */
public final class DueloService {

    /** Un duelo propuesto pendiente de aceptación. */
    public record Duelo(long retador, long retado, long apuesta) {
    }

    public enum Estado { OK, RETADOR_SIN_SALDO, RETADO_SIN_SALDO }

    /** Resultado de resolver: estado + id del ganador (si {@code OK}). */
    public record Resultado(Estado estado, long ganador) {
    }

    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;
    private final BatallaService.Aleatorio azar;
    private final Map<Long, Duelo> pendientes = new ConcurrentHashMap<>();
    private final AtomicLong siguienteId = new AtomicLong(1);

    public DueloService(EconomiaRepositorio economia, UsuarioDiscordRepositorio usuarios,
                        BatallaService.Aleatorio azar) {
        this.economia = economia;
        this.usuarios = usuarios;
        this.azar = azar;
    }

    /** Constructor de producción: azar real. */
    public DueloService(EconomiaRepositorio economia, UsuarioDiscordRepositorio usuarios) {
        this(economia, usuarios, () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Registra un duelo propuesto y devuelve su id. */
    public long proponer(Duelo duelo) {
        long id = siguienteId.getAndIncrement();
        pendientes.put(id, duelo);
        return id;
    }

    /** Recupera y elimina un duelo (se consume al aceptar/rechazar). */
    public Optional<Duelo> consumir(long id) {
        return Optional.ofNullable(pendientes.remove(id));
    }

    /** Resuelve el duelo: cobra a ambos, sortea el ganador y le paga el total. */
    public Resultado resolver(Duelo d) {
        usuarios.obtenerOCrear(d.retador());
        usuarios.obtenerOCrear(d.retado());
        if (!economia.gastar(d.retador(), d.apuesta(), "duelo")) {
            return new Resultado(Estado.RETADOR_SIN_SALDO, 0);
        }
        if (!economia.gastar(d.retado(), d.apuesta(), "duelo")) {
            economia.ingresar(d.retador(), d.apuesta(), "duelo_undo"); // devolver al retador
            return new Resultado(Estado.RETADO_SIN_SALDO, 0);
        }
        long ganador = azar.next() < 0.5 ? d.retador() : d.retado();
        economia.ingresar(ganador, d.apuesta() * 2, "duelo_premio");
        return new Resultado(Estado.OK, ganador);
    }
}
