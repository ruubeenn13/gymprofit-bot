package com.gymprofit.bot.jobs;

import com.gymprofit.bot.services.BolsaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mueve los precios de la bolsa ficticia cada {@value #INTERVALO_MIN} minutos (random walk por
 * volatilidad + eventos de mercado). Solo corre mientras el bot está arriba; es suficiente para que
 * los precios evolucionen entre sesiones.
 */
public final class BolsaJob {

    private static final Logger log = LoggerFactory.getLogger(BolsaJob.class);
    private static final long INTERVALO_MIN = 12;

    private final BolsaService bolsa;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gymprobot-bolsa");
        t.setDaemon(true);
        return t;
    });

    public BolsaJob(BolsaService bolsa) {
        this.bolsa = bolsa;
    }

    public void iniciar() {
        scheduler.scheduleAtFixedRate(this::mover, INTERVALO_MIN, INTERVALO_MIN, TimeUnit.MINUTES);
    }

    public void detener() {
        scheduler.shutdownNow();
    }

    private void mover() {
        try {
            bolsa.tick();
        } catch (RuntimeException e) {
            log.warn("Fallo en el job de la bolsa", e);
        }
    }
}
