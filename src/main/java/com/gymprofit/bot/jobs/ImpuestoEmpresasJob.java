package com.gymprofit.bot.jobs;

import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Impuesto;
import com.gymprofit.bot.services.ImpuestoEmpresasService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cobra el <b>impuesto semanal</b> de las empresas los <b>lunes a las 02:00 Europe/Madrid</b> (F5b):
 * por cada empresa quema del bote la cuota que le toca por su nivel ({@link Impuesto#cuota}) y, si el
 * bote no llega, le anota un impago; a {@link Impuesto#MOROSIDAD_MAX} impagos consecutivos la empresa
 * quiebra y se disuelve. Toda la regla vive en {@link ImpuestoEmpresasService}; el job solo la dispara
 * en su franja y avisa del resultado en el canal privado de cada empresa.
 *
 * <p>El lunes a las 02:00 es a propósito: se cobra <b>antes</b> de la nómina diaria de las 03:00
 * ({@code NominaEmpresasJob}), de modo que la cuota se resta del bote de la semana antes de repartir
 * dividendos con lo que quede. La franja de madrugada evita chocar con la actividad de la comunidad.</p>
 *
 * <p>Se reprograma a sí mismo tras cada ejecución calculando el próximo lunes 02:00 en hora local
 * (aguanta los cambios de horario), igual que {@code NominaEmpresasJob}. El cobro de cada empresa va
 * envuelto en su propio try/catch: un fallo en una no tumba el cobro del resto (best-effort). El aviso
 * es best-effort también y solo se manda cuando hay algo que decir (morosa o quiebra): un cobro normal
 * (PAGA) no avisa para no generar spam semanal.</p>
 */
public final class ImpuestoEmpresasJob {

    private static final Logger log = LoggerFactory.getLogger(ImpuestoEmpresasJob.class);
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private static final LocalTime HORA = LocalTime.of(2, 0);
    private static final DayOfWeek DIA = DayOfWeek.MONDAY;

    private final EmpresaRepositorio empresas;
    private final ImpuestoEmpresasService impuesto;
    private final JDA jda;
    private final Clock clock;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gymprobot-impuesto-empresas");
                t.setDaemon(true);
                return t;
            });

    public ImpuestoEmpresasJob(EmpresaRepositorio empresas, ImpuestoEmpresasService impuesto,
                               JDA jda, Clock clock) {
        this.empresas = empresas;
        this.impuesto = impuesto;
        this.jda = jda;
        this.clock = clock;
    }

    /** Programa el primer cobro (y con él, todos los siguientes). */
    public void iniciar() {
        programarSiguiente();
    }

    /** Detiene el scheduler (parada limpia en el shutdown hook). */
    public void detener() {
        scheduler.shutdownNow();
    }

    /**
     * Espera desde {@code ahora} hasta el próximo lunes a las 02:00 <b>locales</b>. Si ya pasó ese
     * instante este lunes (o no es lunes), apunta al lunes que viene. En hora local a propósito: tras
     * un cambio de horario el cobro sigue saliendo a las 02:00 de reloj. Package-private para el test.
     */
    static Duration esperaHastaProximoLunes(ZonedDateTime ahora) {
        ZonedDateTime prox = ahora.toLocalDate().atTime(HORA).atZone(ahora.getZone())
                .with(TemporalAdjusters.nextOrSame(DIA));
        if (!prox.isAfter(ahora)) {
            prox = prox.plusWeeks(1);
        }
        return Duration.between(ahora, prox);
    }

    private void programarSiguiente() {
        Duration espera = esperaHastaProximoLunes(ZonedDateTime.now(clock.withZone(ZONA)));
        scheduler.schedule(this::tick, espera.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Impuesto de empresas programado para dentro de {} min", espera.toMinutes());
    }

    private void tick() {
        // Lo primero es reprogramar la semana que viene: un fallo cobrando jamás mata el ciclo semanal.
        programarSiguiente();
        cobrar();
    }

    /** Cobra la cuota a todas las empresas. Público para invocarlo en manual/tests. */
    public void cobrar() {
        List<Empresa> todas;
        try {
            todas = empresas.todas();
        } catch (RuntimeException e) {
            log.error("No se pudo listar empresas; impuesto abortado", e);
            return;
        }
        for (Empresa e : todas) {
            try {
                // Cada empresa aislada: si una peta (BD, datos raros), las demás cobran igual.
                ImpuestoEmpresasService.Resolucion r = impuesto.aplicar(e);
                avisar(e, r);
            } catch (RuntimeException ex) {
                log.warn("Fallo cobrando el impuesto de la empresa {}", e.id(), ex);
            }
        }
    }

    /**
     * Aviso al canal privado de la empresa (F4, si existe). PAGA no avisa (anti-spam): solo se notifica
     * la morosidad y la quiebra, que es lo que exige acción del dueño. En ES porque es un post de canal,
     * no la respuesta a un usuario. Best-effort: sin canal o con JDA aún sin conectar no revienta nada.
     */
    private void avisar(Empresa e, ImpuestoEmpresasService.Resolucion r) {
        if (jda == null || e.canalId() == null || r.tipo() == ImpuestoEmpresasService.Tipo.PAGA) {
            return;
        }
        TextChannel canal = jda.getTextChannelById(e.canalId());
        if (canal == null) {
            return;
        }
        String clave = r.tipo() == ImpuestoEmpresasService.Tipo.QUIEBRA
                ? "empresa.impuesto.quiebra" : "empresa.impuesto.morosa";
        var embed = EmbedFactory.base(EmbedFactory.Tipo.MODERACION, Messages.ES,
                Messages.get(Messages.ES, "empresa.impuesto.titulo"),
                Messages.get(Messages.ES, clave, r.cuota(), r.impagos(), Impuesto.MOROSIDAD_MAX, r.falta()))
                .build();
        canal.sendMessageEmbeds(embed).queue(null, err ->
                log.warn("No se pudo avisar del impuesto a la empresa {}", e.id(), err));
    }
}
