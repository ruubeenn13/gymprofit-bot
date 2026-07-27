package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EventoActivo;
import com.gymprofit.bot.db.EventoEconomicoRepositorio;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Clima económico global (F5): mantiene el evento activo (persistido en {@link EventoEconomicoRepositorio})
 * y expone su efecto como multiplicadores que consultan los consumidores de la economía (venta,
 * producción, impuesto, curro, bolsa). Sin evento activo todos los getters son <b>neutros</b> (1.0 /
 * NINGUNO), así que un consumidor puede multiplicar siempre sin ramas.
 *
 * <p>{@link #tick(Instant)} lo llama el job periódico: cierra un evento caducado o, si no hay ninguno,
 * a veces ({@link #PROB_EVENTO}) lanza uno aleatorio. El azar se inyecta para los tests (patrón de
 * {@link BolsaService}).
 */
public final class EventoEconomicoService {

    /** Probabilidad de iniciar un evento por pasada del job (cuando no hay ninguno activo). */
    public static final double PROB_EVENTO = 0.04;
    /** Cuánto dura un evento activo. */
    public static final Duration DURACION = Duration.ofHours(24);

    /** Qué hizo el tick (para que el job anuncie inicio/fin). */
    public enum EstadoTick { NADA, INICIADO, FINALIZADO }

    /** Resultado de un tick: qué pasó y con qué evento (null si NADA). */
    public record ResultadoTick(EstadoTick estado, EventoEconomico evento) {
        static ResultadoTick nada() { return new ResultadoTick(EstadoTick.NADA, null); }
    }

    private final EventoEconomicoRepositorio repo;
    private final BatallaService.Aleatorio azar;

    public EventoEconomicoService(EventoEconomicoRepositorio repo, BatallaService.Aleatorio azar) {
        this.repo = repo;
        this.azar = azar;
    }

    /** Constructor de producción: azar real. */
    public EventoEconomicoService(EventoEconomicoRepositorio repo) {
        this(repo, () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Evento activo, si lo hay. */
    public Optional<EventoActivo> activo() {
        return repo.activo();
    }

    public double ventaMult() { return activo().map(a -> a.tipo().ventaMult()).orElse(1.0); }
    public double produccionMult() { return activo().map(a -> a.tipo().produccionMult()).orElse(1.0); }
    public double impuestoMult() { return activo().map(a -> a.tipo().impuestoMult()).orElse(1.0); }
    public double curroMult() { return activo().map(a -> a.tipo().curroMult()).orElse(1.0); }

    public EventoEconomico.BolsaSesgo bolsaSesgo() {
        return activo().map(a -> a.tipo().bolsaSesgo()).orElse(EventoEconomico.BolsaSesgo.NINGUNO);
    }

    /** Lanza un evento concreto (sobrescribe el activo si lo hubiera). Devuelve el que quedó activo. */
    public EventoEconomico lanzar(EventoEconomico evento, Instant ahora) {
        repo.fijar(evento, ahora, ahora.plus(DURACION));
        return evento;
    }

    /** Lanza uno al azar del catálogo. */
    public EventoEconomico lanzarAleatorio(Instant ahora) {
        return lanzar(EventoEconomico.aleatorio(azar.next()), ahora);
    }

    /**
     * Pasada del job: si el activo caducó lo cierra (FINALIZADO); si no hay activo y el dado cae por
     * debajo de {@link #PROB_EVENTO} lanza uno aleatorio (INICIADO); en otro caso NADA.
     */
    public ResultadoTick tick(Instant ahora) {
        Optional<EventoActivo> act = repo.activo();
        if (act.isPresent()) {
            if (act.get().caducado(ahora)) {
                repo.limpiar();
                return new ResultadoTick(EstadoTick.FINALIZADO, act.get().tipo());
            }
            return ResultadoTick.nada();
        }
        if (azar.next() < PROB_EVENTO) {
            return new ResultadoTick(EstadoTick.INICIADO, lanzarAleatorio(ahora));
        }
        return ResultadoTick.nada();
    }
}
