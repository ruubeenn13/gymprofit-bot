package com.gymprofit.bot.jobs;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.services.RepartoNomina;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Paga la <b>nómina diaria</b> de las empresas a las <b>03:00 Europe/Madrid</b>: por cada empresa con
 * bote, reparte {@link RepartoNomina#FRACCION_NOMINA} del bote entre sus miembros según el peso de su
 * rango (ver {@link RepartoNomina}). La franja de madrugada evita chocar con el resto de jobs y con la
 * actividad de la comunidad.
 *
 * <p>Se reprograma a sí mismo tras cada ejecución calculando las próximas 03:00 en hora local (aguanta
 * los cambios de horario), igual que {@code EjercicioDiaJob}. El pago de cada empresa va envuelto en su
 * propio try/catch: un fallo en una no tumba el reparto del resto. Solo se ingresa lo que se ha podido
 * gastar del bote de forma atómica ({@link EmpresaRepositorio#gastarDelBote}): si el gasto no cuadra,
 * esa empresa se salta sin pagar a medias.
 */
public final class NominaEmpresasJob {

    private static final Logger log = LoggerFactory.getLogger(NominaEmpresasJob.class);
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private static final LocalTime HORA_NOMINA = LocalTime.of(3, 0);
    /** Motivo con el que se registra el ingreso en el libro de la economía. */
    private static final String MOTIVO = "nomina_empresa";

    private final EmpresaRepositorio empresas;
    private final EconomiaRepositorio economia;
    private final Clock clock;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gymprobot-nomina-empresas");
                t.setDaemon(true);
                return t;
            });

    public NominaEmpresasJob(EmpresaRepositorio empresas, EconomiaRepositorio economia, Clock clock) {
        this.empresas = empresas;
        this.economia = economia;
        this.clock = clock;
    }

    /** Programa el primer reparto (y con él, todos los siguientes). */
    public void iniciar() {
        programarSiguiente();
    }

    /** Detiene el scheduler (parada limpia en el shutdown hook). */
    public void detener() {
        scheduler.shutdownNow();
    }

    /**
     * Espera desde {@code ahora} hasta las próximas 03:00 <b>locales</b>. Si ya son las 03:00 o más
     * tarde, apunta a mañana. En hora local a propósito: tras un cambio de horario la nómina sigue
     * saliendo a las 03:00 de reloj, no 24 h después.
     */
    static Duration esperaHastaLasTres(ZonedDateTime ahora) {
        ZonedDateTime proxima = ahora.toLocalDate().atTime(HORA_NOMINA).atZone(ahora.getZone());
        if (!proxima.isAfter(ahora)) {
            proxima = ahora.toLocalDate().plusDays(1).atTime(HORA_NOMINA).atZone(ahora.getZone());
        }
        return Duration.between(ahora, proxima);
    }

    private void programarSiguiente() {
        Duration espera = esperaHastaLasTres(ZonedDateTime.now(clock.withZone(ZONA)));
        scheduler.schedule(this::tick, espera.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Nómina de empresas programada para dentro de {} min", espera.toMinutes());
    }

    private void tick() {
        // Lo primero es reprogramar mañana: un fallo repartiendo jamás mata el ciclo diario.
        programarSiguiente();
        repartir();
    }

    /** Reparte la nómina a todas las empresas con bote. Público para invocarlo en manual/tests. */
    public void repartir() {
        List<Empresa> conBote;
        try {
            conBote = empresas.conBote();
        } catch (RuntimeException e) {
            log.error("No se pudo listar las empresas con bote; nómina abortada", e);
            return;
        }

        int empresasPagadas = 0;
        long totalRepartido = 0;
        for (Empresa empresa : conBote) {
            try {
                // Cada empresa aislada: si una peta (BD, datos raros), las demás cobran igual.
                List<RepartoNomina.ParteNomina> partes =
                        RepartoNomina.calcular(empresa.bote(), empresas.miembros(empresa.id()));
                long total = partes.stream().mapToLong(RepartoNomina.ParteNomina::parte).sum();
                if (total <= 0) {
                    continue; // bote demasiado pequeño para repartir algo este día
                }
                // Se gasta del bote ANTES de ingresar: si el gasto atómico no cuadra (saldo justo
                // movido por otra vía), no se paga a nadie de esta empresa. Nunca se ingresa sin haber
                // descontado antes el mismo total del bote.
                if (!empresas.gastarDelBote(empresa.id(), total)) {
                    continue;
                }
                for (RepartoNomina.ParteNomina parte : partes) {
                    if (parte.parte() > 0) {
                        economia.ingresar(parte.discordId(), parte.parte(), MOTIVO);
                    }
                }
                empresasPagadas++;
                totalRepartido += total;
            } catch (RuntimeException e) {
                log.warn("Fallo repartiendo la nómina de la empresa {}", empresa.id(), e);
            }
        }
        if (empresasPagadas > 0) {
            log.info("Nómina repartida: {} empresas pagadas, {} coins en total",
                    empresasPagadas, totalRepartido);
        }
    }
}
