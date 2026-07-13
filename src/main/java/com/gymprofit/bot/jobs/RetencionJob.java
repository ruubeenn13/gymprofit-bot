package com.gymprofit.bot.jobs;

import com.gymprofit.bot.db.SancionRepositorio;
import com.gymprofit.bot.db.WarnRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Job de <b>retención de datos</b> (RGPD): purga periódicamente los datos de moderación viejos para
 * minimizar lo que se conserva. Se ejecuta cada 24 h. Ventanas: avisos ya revocados con más de
 * {@value #DIAS_WARNS_REVOCADOS} días y sanciones con más de {@value #DIAS_SANCIONES} días.
 */
public final class RetencionJob {

    private static final Logger log = LoggerFactory.getLogger(RetencionJob.class);

    /** Retención de avisos revocados: 6 meses. */
    public static final int DIAS_WARNS_REVOCADOS = 180;
    /** Retención del historial de sanciones: 12 meses. */
    public static final int DIAS_SANCIONES = 365;

    private static final long RETARDO_INICIAL_MIN = 10;
    private static final long INTERVALO_MIN = 24 * 60;

    private final WarnRepositorio warns;
    private final SancionRepositorio sanciones;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gymprobot-retencion");
        t.setDaemon(true);
        return t;
    });

    public RetencionJob(WarnRepositorio warns, SancionRepositorio sanciones) {
        this.warns = warns;
        this.sanciones = sanciones;
    }

    /** Programa la purga cada 24 h (primera pasada a los {@value #RETARDO_INICIAL_MIN} min). */
    public void iniciar() {
        scheduler.scheduleAtFixedRate(this::purgar,
                RETARDO_INICIAL_MIN, INTERVALO_MIN, TimeUnit.MINUTES);
    }

    /** Detiene el scheduler (parada limpia). */
    public void detener() {
        scheduler.shutdownNow();
    }

    /** Ejecuta una pasada de purga. Público para poder invocarlo en tests. */
    public void purgar() {
        try {
            Instant limiteWarns = Instant.now().minus(Duration.ofDays(DIAS_WARNS_REVOCADOS));
            Instant limiteSanciones = Instant.now().minus(Duration.ofDays(DIAS_SANCIONES));
            int avisos = warns.purgarRevocadosAnterioresA(limiteWarns);
            int historial = sanciones.purgarAnterioresA(limiteSanciones);
            if (avisos > 0 || historial > 0) {
                log.info("Retención: purgados {} avisos revocados y {} sanciones antiguas",
                        avisos, historial);
            }
        } catch (RuntimeException e) {
            log.warn("Fallo en el job de retención de datos", e);
        }
    }
}
