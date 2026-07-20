package com.gymprofit.bot.jobs;

import com.gymprofit.bot.db.PersonajeRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Regenera la energía de todos los personajes cada {@value #INTERVALO_MIN} minutos
 * ({@code +}{@value #REGEN} hasta 100). Es el freno de ritmo del RPG: trabajar/entrenar gasta
 * energía y esta vuelve poco a poco.
 *
 * <p>Este goteo es solo el <b>mínimo vital</b> para quien no juega: el grueso de la energía se gana
 * durmiendo ({@code /descansar}). Antes regalaba +10 y recargaba solo en ~5 h, lo que equivalía a
 * más acciones y más coins por día sin participar; el ritmo total se mantiene, pero ahora pasa por
 * el ciclo de descanso.
 *
 * <p>A los dormidos no les regenera: ya cobran su energía al despertar, y sumar las dos vías sería
 * doble ración (ver {@code PersonajeRepositorio#regenerarEnergia}).
 */
public final class EnergiaJob {

    private static final Logger log = LoggerFactory.getLogger(EnergiaJob.class);
    private static final long INTERVALO_MIN = 30;
    /** Energía recuperada por tick. La mitad que antes: el resto se gana durmiendo. */
    public static final int REGEN = 5;

    private final PersonajeRepositorio personajes;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gymprobot-energia");
        t.setDaemon(true);
        return t;
    });

    public EnergiaJob(PersonajeRepositorio personajes) {
        this.personajes = personajes;
    }

    public void iniciar() {
        scheduler.scheduleAtFixedRate(this::regenerar, INTERVALO_MIN, INTERVALO_MIN, TimeUnit.MINUTES);
    }

    public void detener() {
        scheduler.shutdownNow();
    }

    private void regenerar() {
        try {
            int afectados = personajes.regenerarEnergia(REGEN);
            if (afectados > 0) {
                log.debug("Energía regenerada a {} personajes", afectados);
            }
        } catch (RuntimeException e) {
            log.warn("Fallo en el job de energía", e);
        }
    }
}
